package com.weather.gametest;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.dispatch.InstructionSet;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.interpreter.instructions.WeatherInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.weather.ExampleMod;
import com.weather.cobblemon.CobblemonEventListener;
import com.weather.config.ServerConfig;
import com.weather.logic.BattleWeatherManager;
import com.weather.logic.BattleWeatherType;
import dev.architectury.platform.Platform;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WeatherGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void weatherPermissionsAndTiming(GameTestHelper helper) throws Exception {
        ServerLevel world = helper.getLevel();
        try {
            testManager(helper, world);
            testInstructions(helper, world);
            helper.succeed();
        } finally {
            configure("{}");
            world.setWeatherParameters(24000, 0, false, false);
        }
    }

    private void testManager(GameTestHelper h, ServerLevel world) throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        Set<UUID> active = new HashSet<>(Set.of(first, second));
        BattleWeatherManager manager = new BattleWeatherManager(active::contains);
        ServerConfig config = configure("{\"battleWeatherDurationTicks\":1200,\"weatherChangeCooldownTicks\":100}");
        world.setWeatherParameters(1000, 0, false, false);
        world.setRainLevel(1); // Old rain can still be fading visually after clear weather was set.
        h.assertTrue(!manager.applyThunderstorm(world, first, "thunder", 0, config), "Thunder must require rain");
        h.assertTrue(!manager.applyThunderstorm(world, first, "thunderbolt", 0, config), "Thunderbolt must require rain");
        h.assertTrue(!manager.applyThunderstorm(world, first, "tackle", 0, config), "Unrelated moves cannot start storms");
        h.assertTrue(manager.applyThunderstorm(world, first, "wildboltstorm", 0, config), "Wildbolt Storm must start from clear weather");
        assertWeather(h, world, true, true, 1200);
        h.assertTrue(!manager.applyWeatherFromBattle(world, first, BattleWeatherType.SUN, 2, 99, config), "Cooldown must block early weather changes");
        h.assertTrue(!manager.applyThunderstorm(world, first, "wildboltstorm", 99, config), "Wildbolt Storm cannot bypass cooldown");
        h.assertTrue(manager.applyWeatherFromBattle(world, first, BattleWeatherType.SUN, 2, 100, config), "Cooldown boundary must allow change");
        h.assertTrue(!world.getLevelData().isRaining() && world.getServer().getWorldData().overworldData().getClearWeatherTime() == 1200, "Clear weather must use configured duration");
        h.assertTrue(!manager.applyWeatherFromBattle(world, first, BattleWeatherType.RAIN, 0, 200, config), "Primal weather must resist lower priority");
        manager.releasePriority(world, first);
        h.assertTrue(manager.applyWeatherFromBattle(world, first, BattleWeatherType.RAIN, 0, 200, config), "Ending primal battle weather releases its lock");
        world.setRainLevel(0); // Rain was just set; visuals have not caught up yet.
        h.assertTrue(manager.applyThunderstorm(world, first, "thunderbolt", 300, config), "Thunderbolt must upgrade existing rain");
        h.assertTrue(!manager.applyWeatherFromBattle(world, second, BattleWeatherType.SUN, 2, 400, config), "Another live battle cannot steal weather by default");
        active.remove(first);
        h.assertTrue(manager.applyWeatherFromBattle(world, second, BattleWeatherType.SUN, 0, 400, config), "Ended battle cannot retain a weather lock");
        config = configure("{\"weatherChangeCooldownTicks\":0,\"clearWeatherOnBattleEnd\":true}");
        manager.applyWeatherFromBattle(world, second, BattleWeatherType.RAIN, 0, 500, config);
        active.remove(second);
        manager.tick(world, 501, config);
        h.assertTrue(!world.getLevelData().isRaining(), "All battle endings must clear owned weather when configured");
        active.add(first); active.add(second);
        config = configure("{\"weatherChangeCooldownTicks\":0,\"allowCrossBattleOverride\":true}");
        manager.applyWeatherFromBattle(world, first, BattleWeatherType.RAIN, 0, 600, config);
        h.assertTrue(manager.applyWeatherFromBattle(world, second, BattleWeatherType.SUN, 1, 600, config), "Configured cross-battle priority override must work");
        h.assertTrue(!manager.applyWeatherFromBattle(world, first, BattleWeatherType.RAIN, 0, 600, config), "Lower cross-battle priority cannot override");
        h.assertTrue(manager.applyWeatherFromBattle(world, first, BattleWeatherType.RAIN, 0, 24600, config), "Expired priority must be released");
        config = configure("{\"enableWeatherIntegration\":false,\"weatherChangeCooldownTicks\":0}");
        h.assertTrue(!manager.applyThunderstorm(world, first, "wildboltstorm", 50000, config), "Master switch must disable storms");
        config = configure("{\"enableThunderstormIntegration\":false,\"weatherChangeCooldownTicks\":0}");
        h.assertTrue(!manager.applyThunderstorm(world, first, "wildboltstorm", 50000, config), "Storm switch must disable Wildbolt Storm");
        h.assertTrue(!manager.applyWeatherFromBattle(world, first, BattleWeatherType.THUNDERSTORM, 2, 50000, config), "Storm switch cannot be bypassed via ordinary weather path");
        ServerLevel nether = world.getServer().getLevel(Level.NETHER);
        h.assertTrue(!manager.applyWeatherFromBattle(nether, first, BattleWeatherType.RAIN, 0, 50000, config), "Only Overworld weather may change");
    }

    private void testInstructions(GameTestHelper h, ServerLevel world) throws Exception {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        BattlePokemon allowed = pokemon(), denied = pokemon();
        PlayerBattleActor a = new PlayerBattleActor(player.getUUID(), List.of(allowed));
        PlayerBattleActor b = new PlayerBattleActor(UUID.randomUUID(), List.of(denied));
        a.setShowdownId("p1"); b.setShowdownId("p2");
        PokemonBattle battle = new PokemonBattle(new BattleFormat(), new BattleSide(a), new BattleSide(b));
        String allowId = "p1a: " + allowed.getUuid(), denyId = "p2a: " + denied.getUuid();
        configure("{\"weatherWhitelist\":[\"" + player.getUUID() + "\"],\"weatherChangeCooldownTicks\":0,\"battleWeatherDurationTicks\":800}");
        clear(world);
        weather(battle, "|-weather|RainDance|[from] ability: Drizzle|[of] " + denyId);
        h.assertTrue(!world.getLevelData().isRaining(), "Opponent's ability must not borrow whitelisted player's permission");
        weather(battle, "|-weather|RainDance|[from] ability: Drizzle|[of] " + allowId);
        assertWeather(h, world, true, false, 800);
        clear(world);
        weather(battle, "|-weather|RainDance|[from] ability: Drizzle");
        h.assertTrue(!world.getLevelData().isRaining(), "Unattributed abilities must fail closed under whitelist");
        weather(battle, "|-weather|RainDance|[from] ability: Drizzle|[of] " + allowId + "|[upkeep]");
        h.assertTrue(!world.getLevelData().isRaining(), "Upkeep must not refresh world weather");
        move(battle, "|move|" + denyId + "|Rain Dance");
        weather(battle, "|-weather|RainDance");
        h.assertTrue(!world.getLevelData().isRaining(), "Move source must be checked independently of other participants");
        move(battle, "|move|" + allowId + "|Rain Dance");
        h.assertTrue(!world.getLevelData().isRaining(), "Attempted/failed weather move alone cannot change weather");
        weather(battle, "|-weather|RainDance");
        assertWeather(h, world, true, false, 800);
        clear(world);
        move(battle, "|move|" + denyId + "|Wildbolt Storm");
        h.assertTrue(!world.getLevelData().isRaining(), "Unlisted Wildbolt Storm user must be blocked");
        move(battle, "|move|" + allowId + "|Thunder");
        move(battle, "|move|" + allowId + "|Thunderbolt");
        h.assertTrue(!world.getLevelData().isRaining(), "Neither Thunder nor Thunderbolt can start rain");
        move(battle, "|move|" + allowId + "|Wildbolt Storm");
        assertWeather(h, world, true, true, 800);
        clear(world);
        configure("{\"weatherWhitelist\":[],\"weatherChangeCooldownTicks\":0}");
        weather(battle, "|-weather|RainDance|[from] ability: Drizzle|[of] " + denyId);
        h.assertTrue(world.getLevelData().isRaining(), "Empty whitelist must allow any player's battle effects");
        h.assertTrue(CobblemonEventListener.isAllowed(null, ExampleMod.getConfig()), "Empty whitelist retains wild effects");
        configure("{\"weatherWhitelist\":[\"" + player.getUUID() + "\"],\"weatherChangeCooldownTicks\":0}");
        h.assertTrue(CobblemonEventListener.isAllowed(allowed, ExampleMod.getConfig()), "UUID whitelist must work in game");
        h.assertTrue(!CobblemonEventListener.isAllowed(denied, ExampleMod.getConfig()), "Unlisted account must be rejected");
        h.assertTrue(!CobblemonEventListener.isAllowed(null, ExampleMod.getConfig()), "Unknown/wild sources must be denied under whitelist");
        var commands = world.getServer().getCommands().getDispatcher();
        var admin = world.getServer().createCommandSourceStack();
        int changed = commands.execute("cobbleweather debug sun", admin);
        h.assertTrue(changed == 1 && !world.getLevelData().isRaining(), "Administrative debug command must work");
        configure("{\"weatherWhitelist\":[\"Nobody\"],\"weatherChangeCooldownTicks\":0}");
        changed = commands.execute("cobbleweather thunderstorm", player.createCommandSourceStack().withPermission(2));
        h.assertTrue(changed == 0 && !world.getLevelData().isRaining(), "Unlisted operators cannot bypass whitelist using addon commands");
        ShowdownInterpreter.INSTANCE.getLastCauser().remove(battle.getBattleId());
    }

    private static BattlePokemon pokemon() {
        PokemonProperties properties = new PokemonProperties();
        properties.setSpecies("pikachu");
        return BattlePokemon.Companion.playerOwned(properties.create());
    }

    private static void weather(PokemonBattle battle, String line) {
        new WeatherInstruction(new BattleMessage(line)).invoke(battle);
        drain(battle);
    }

    private static void move(PokemonBattle battle, String line) {
        InstructionSet set = new InstructionSet();
        MoveInstruction instruction = new MoveInstruction(set, new BattleMessage(line));
        set.getInstructions().add(instruction);
        instruction.invoke(battle);
        drain(battle);
    }

    private static void drain(PokemonBattle battle) {
        // Execute the actual queued actions without waiting for client animations in this server test.
        while (!battle.getDispatches().isEmpty()) battle.getDispatches().removeFirst().invoke(battle);
    }

    private static void clear(ServerLevel world) {
        ExampleMod.getWeatherManager().applyDebugWeather(world, BattleWeatherType.CLEAR, ExampleMod.getConfig());
        world.setRainLevel(0); world.setThunderLevel(0);
    }

    private static void assertWeather(GameTestHelper h, ServerLevel world, boolean rain, boolean thunder, int duration) {
        h.assertTrue(world.getLevelData().isRaining() == rain && world.getLevelData().isThundering() == thunder,
                "Unexpected rain/thunder state");
        h.assertTrue(world.getServer().getWorldData().overworldData().getRainTime() == duration && world.getServer().getWorldData().overworldData().getThunderTime() == duration,
                "Weather duration must match configuration");
    }

    private static ServerConfig configure(String json) throws Exception {
        var path = Platform.getConfigFolder().resolve("cobblemon_weather.json");
        Files.createDirectories(path.getParent()); Files.writeString(path, json);
        ExampleMod.reloadConfig();
        return ExampleMod.getConfig();
    }
}
