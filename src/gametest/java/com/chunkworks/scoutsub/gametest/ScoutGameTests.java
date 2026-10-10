/*
 * The Scout - a one-man submarine for Submersibles.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.scoutsub.gametest;

import com.chunkworks.submersibles.Submarine;
import com.chunkworks.submersibles.SubmersiblesContent;
import com.chunkworks.submersibles.Torpedo;
import com.chunkworks.submersibles.api.SubmarineProfile;
import com.chunkworks.submersibles.api.Submersibles;
import com.chunkworks.submersibles.domain.DiveInput;
import com.chunkworks.vanillawheels.ModContent;
import com.chunkworks.vanillawheels.Vehicle;
import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.domain.Condition;
import com.chunkworks.vanillawheels.domain.Vec;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Scout on a headless server, in a tank of water 48 long, 24 wide, walled with barriers: its
 * two profiles make it a submarine of one seat on skids, with a locker of one row, one spotlight,
 * one screw, two upgrade slots and one tube; its pod crafts from copper at a crafting table and,
 * with an engine, makes the packed submarine, which set down on open water floats at its draft;
 * it holds its depth and rises to float; it runs up to its own top speed, twice the Explorer's;
 * its pilot breathes at depth and a second is turned away; its locker opens through the tail's
 * hatch and from the seat; and a torpedo leaves the tube under its chin ahead of the nose.
 */
@GameTestHolder("scout_sub")
@PrefixGameTestTemplate(false)
public final class ScoutGameTests {
    private static final ResourceLocation SCOUT = ResourceLocation.fromNamespaceAndPath("scout_sub", "scout");
    /** The tank (devtools/gameteststructures/tank.snbt), in the test's own blocks: x along it, z across. */
    private static final int LENGTH = 48, HEIGHT = 24, WIDTH = 24;
    /** The pool's water fills y 1 to POOL; its surface is at POOL + 1. */
    private static final int POOL = 14;
    /** Its profile's numbers: thrust 0.030, the propeller's slip 0.020 and the hull's drag 0.044. */
    private static final double TOP_SPEED = 0.030 * (1.0 - 0.064) / 0.064;
    private static final double DRAFT = 0.45;

    public ScoutGameTests() {}

    // ------------------------------------------------------------------ the rigs

    /** effects: lays the tank's stone floor (y 0) and fills it with water from y 1 up to {@code depth}, wall to wall */
    private static void pool(GameTestHelper helper, int depth) {
        for (int x = 0; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= depth; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
    }

    /** effects: returns a fuelled Scout at (x, y, z) facing {@code yaw}, in the level */
    private static Submarine sub(GameTestHelper helper, double x, double y, double z, float yaw) {
        Vehicle v = Vehicle.create(helper.getLevel(), SCOUT, helper.absoluteVec(new Vec3(x, y, z)), yaw);
        helper.assertTrue(v instanceof Submarine, "the Scout is made as Submersibles' submarine: " + v);
        v.setFuel(v.tank().capacity());
        helper.getLevel().addFreshEntity(v);
        return (Submarine) v;
    }

    /** effects: puts an armor stand at {@code s}'s controls -- a pilot that is no player, so the server dives by the script -- and returns it */
    private static ArmorStand standIn(GameTestHelper helper, Submarine s) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel());
        helper.assertTrue(stand != null, "an armor stand");
        stand.setPos(s.getX(), s.getY(), s.getZ());
        helper.getLevel().addFreshEntity(stand);
        helper.assertTrue(stand.startRiding(s, true), "the stand-in takes the controls");
        return stand;
    }

    /** effects: returns the script's input: thrust, rudder and planes, powered, the world's facts filled in each tick */
    private static DiveInput dive(int forward, int turn, int lift) {
        return new DiveInput(forward, turn, lift, true, true, 1.0, false, 0.0, 0.0, 0.0);
    }

    /** effects: returns a villager with no AI at {@code at} (absolute), in the level: ticked as every mob is, so it can run out of air */
    private static Villager villager(GameTestHelper helper, Vec3 at) {
        Villager v = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(v != null, "a villager");
        v.setNoAi(true);
        v.setPos(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /** A server player with a connection that goes nowhere, so menus and item use take the real path. */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 at) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer sp = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, sp, cookie);
        sp.setGameMode(GameType.SURVIVAL);
        Vec3 abs = helper.absoluteVec(at);
        sp.teleportTo(abs.x, abs.y, abs.z);
        return sp;
    }

    private static void logOff(ServerPlayer sp) {
        sp.connection.disconnect(net.minecraft.network.chat.Component.literal("test complete"));
    }

    /** effects: returns the crafting grid's result recipe, if any, for {@code items} laid row by row in a {@code w} by {@code h} grid */
    private static Optional<RecipeHolder<CraftingRecipe>> craft(GameTestHelper helper, int w, int h, List<ItemStack> items) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(w, h, items), helper.getLevel());
    }

    /** effects: returns the packed Scout, crafted as a player would: the pod, then the pod and an engine */
    private static ItemStack craftedScout(GameTestHelper helper) {
        List<ItemStack> grid = new ArrayList<>();
        Item copper = Items.COPPER_BLOCK;
        for (Item item : new Item[] {Items.GLASS, Items.GLASS, Items.GLASS, copper, Items.BARREL, copper, Items.AIR, copper, Items.AIR}) {
            grid.add(new ItemStack(item));
        }
        Optional<RecipeHolder<CraftingRecipe>> pod = craft(helper, 3, 3, grid);
        helper.assertTrue(pod.isPresent(), "copper under a bubble round a barrel crafts something");
        helper.assertValueEqual(pod.get().id(), ResourceLocation.fromNamespaceAndPath("scout_sub", "scout_chassis"), "the Scout's pod");
        ItemStack chassis = pod.get().value().assemble(CraftingInput.of(3, 3, grid), helper.getLevel().registryAccess());
        helper.assertTrue(chassis.is(ModContent.CHASSIS.get()) && VanillaWheels.vehicleOf(chassis).equals(Optional.of(SCOUT)), "a chassis for the Scout: " + chassis);
        List<ItemStack> pair = List.of(chassis, new ItemStack(ModContent.ENGINE.get()));
        Optional<RecipeHolder<CraftingRecipe>> whole = craft(helper, 2, 1, pair);
        helper.assertTrue(whole.isPresent(), "the pod and an engine craft something");
        helper.assertValueEqual(whole.get().id(), SCOUT, "the Scout itself");
        return whole.get().value().assemble(CraftingInput.of(2, 1, pair), helper.getLevel().registryAccess());
    }

    // ------------------------------------------------------------------ the tests

    @GameTest(template = "tank", timeoutTicks = 40)
    public void theProfilesMakeItASubmarineOfOneSeatOnSkids(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        VehicleProfile p = VanillaWheels.profile(registries, SCOUT).map(h -> h.value()).orElse(null);
        helper.assertTrue(p != null, "scout_sub:scout is a vehicle");
        helper.assertValueEqual(p.seats().size(), 1, "one seat");
        helper.assertTrue(p.seats().get(0).driver() && p.seats().get(0).eye().isPresent(), "the pilot's, the eye up in the dome");
        helper.assertTrue(!p.wheels().drawn() && p.wheels().positions().size() == 4, "resting on its skids' ends");
        helper.assertValueEqual(p.storage().orElseThrow().chests().size(), 1, "one locker");
        helper.assertValueEqual(p.storage().orElseThrow().chests().get(0).rows(), 1, "of one row");
        helper.assertValueEqual(p.headlights().orElseThrow().at().size(), 1, "one spotlight");
        helper.assertValueEqual(p.headlights().orElseThrow().range(), 16, "reaching 16 blocks");
        helper.assertValueEqual(p.paint().orElseThrow().factory(), Optional.of(0xf2c230), "yellow from the factory");
        helper.assertValueEqual(p.sounds().engine(), Optional.of(ResourceLocation.fromNamespaceAndPath("scout_sub", "engine")), "its own loop");
        SubmarineProfile sp = Submersibles.submarine(registries, SCOUT).orElse(null);
        helper.assertTrue(sp != null, "and a submarine");
        helper.assertValueEqual(sp.upgrades(), 2, "two upgrade slots");
        helper.assertValueEqual(sp.weapons().size(), 1, "one tube");
        helper.assertValueEqual(sp.propellers().size(), 1, "one screw");
        helper.assertTrue(sp.hatch().isPresent(), "a way out over the dome");
        helper.assertValueEqual(p.durability(), 1.5, "fragile: two thirds of the wear (its Vanilla Wheels profile's since Submersibles 1.1.0)");
        Submarine s = sub(helper, 24.5, 2, 12.5, 0.0f);
        helper.assertValueEqual(s.getName().getString(), "Scout", "named");
        helper.assertTrue(Math.abs(s.hullform().topSpeed() - TOP_SPEED) < 1e-9, "its top speed by its numbers: " + s.hullform().topSpeed());
        helper.succeed();
    }

    @GameTest(template = "tank", timeoutTicks = 40)
    public void itsPodCraftsAtACraftingTableAndWithAnEngineMakesThePackedSubmarine(GameTestHelper helper) {
        ItemStack packed = craftedScout(helper);
        helper.assertTrue(packed.is(ModContent.VEHICLE_ITEM.get()), "a packed vehicle: " + packed);
        helper.assertValueEqual(VanillaWheels.vehicleOf(packed), Optional.of(SCOUT), "the Scout");
        // Every vehicle's hull is the same item: only the Scout's makes a Scout.
        ItemStack other = ModContent.chassisStack(ResourceLocation.fromNamespaceAndPath("scout_sub", "not_the_scout"));
        Optional<RecipeHolder<CraftingRecipe>> wrong = craft(helper, 2, 1, List.of(other, new ItemStack(ModContent.ENGINE.get())));
        helper.assertTrue(wrong.isEmpty(), "another vehicle's hull and an engine make nothing here: " + wrong.map(RecipeHolder::id));
        helper.succeed();
    }

    @GameTest(template = "tank", timeoutTicks = 300)
    public void aCraftedScoutSetDownOnOpenWaterFloatsAtItsDraft(GameTestHelper helper) {
        pool(helper, POOL);
        // Over open water, for the tick it takes: a sub set down beside a dock has its tail under the dock.
        ServerPlayer sp = player(helper, "launcher", new Vec3(24.5, POOL + 2, 2.5));
        sp.setYRot(0.0f);   // facing +z, down at the water 4.4 blocks off, within reach
        sp.setXRot(55.0f);
        sp.setItemInHand(InteractionHand.MAIN_HAND, craftedScout(helper));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    sp.gameMode.useItem(sp, helper.getLevel(), sp.getMainHandItem(), InteractionHand.MAIN_HAND);
                    helper.assertTrue(sp.getMainHandItem().isEmpty(), "the item was used up");
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    List<Submarine> subs = helper.getLevel().getEntitiesOfClass(Submarine.class, new AABB(sp.blockPosition()).inflate(10.0));
                    helper.assertValueEqual(subs.size(), 1, "one Scout set down on the water");
                    double wet = subs.get(0).submersion();
                    helper.assertTrue(Math.abs(wet - DRAFT) <= 0.08, "floating at its draft: " + wet + " under water");
                    logOff(sp);
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 500)
    public void itHoldsItsDepthThenRisesToFloatAtItsDraft(GameTestHelper helper) {
        pool(helper, POOL);
        Submarine s = sub(helper, 24.5, 4, 12.5, 0.0f);
        standIn(helper, s);
        s.setScriptedDive(dive(0, 0, 0));
        double[] y0 = new double[1];
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> y0[0] = s.getY())
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(Math.abs(s.getY() - y0[0]) <= 0.1, "it held its depth: " + (s.getY() - y0[0]));
                    s.setScriptedDive(dive(0, 0, 1));
                })
                .thenIdle(240)
                .thenExecute(() -> {
                    double wet = s.submersion();
                    helper.assertTrue(Math.abs(wet - DRAFT) <= 0.08, "up, it floats at its draft: " + wet);
                    helper.assertTrue(Math.abs(s.getDeltaMovement().y) <= 0.02, "and stays: " + s.getDeltaMovement().y);
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 400)
    public void aheadItRunsUpToItsOwnTopSpeed(GameTestHelper helper) {
        pool(helper, POOL);
        Submarine s = sub(helper, 4.5, 5, 12.5, -90.0f);   // facing +x, down the tank
        standIn(helper, s);
        s.setScriptedDive(dive(1, 0, 0));
        double startX = helper.absoluteVec(new Vec3(4.5, 0, 0)).x;
        Vec3[] from = new Vec3[1];
        StringBuilder trace = new StringBuilder();
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (tick[0]++ % 10 == 0) {
                trace.append(String.format(Locale.ROOT, " t%d:x%.1f,v%.3f,power%.2f", tick[0], s.getX() - startX, s.getDeltaMovement().horizontalDistance(), s.power()));
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(s.getX() - startX > 30.0, "down the tank:" + trace))
                .thenExecute(() -> from[0] = s.position())
                .thenIdle(10)
                .thenExecute(() -> {
                    double speed = s.position().subtract(from[0]).horizontalDistance() / 10.0;
                    helper.assertTrue(Math.abs(speed - TOP_SPEED) <= 0.05 * TOP_SPEED, "at its top speed " + TOP_SPEED + ": " + speed + trace);
                })
                .thenSucceed();
    }

    @GameTest(template = "tank", timeoutTicks = 400)
    public void itsPilotBreathesAtDepthAndASecondIsTurnedAway(GameTestHelper helper) {
        pool(helper, POOL);
        Submarine s = sub(helper, 24.5, 4, 12.5, 0.0f);
        Villager pilot = villager(helper, s.position());
        helper.assertTrue(pilot.startRiding(s, true), "aboard");
        Villager second = villager(helper, helper.absoluteVec(new Vec3(36.5, 2, 12.5)));
        helper.assertTrue(!second.startRiding(s, false), "a second finds no seat");
        helper.assertValueEqual(s.getPassengers().size(), 1, "one aboard");
        helper.startSequence()
                .thenIdle(300)
                .thenExecute(() -> {
                    helper.assertTrue(pilot.isEyeInFluid(FluidTags.WATER), "the pilot's eyes are under water");
                    helper.assertTrue(pilot.getAirSupply() >= pilot.getMaxAirSupply() - 1, "and they breathe: air " + pilot.getAirSupply());
                    helper.assertValueEqual(pilot.getHealth(), pilot.getMaxHealth(), "unhurt after fifteen seconds down");
                })
                .thenSucceed();
    }

    /**
     * The locker: a chest's single row hidden in the solid tail under its hatch. A click on the
     * hatch from above, as from a jetty, sent as the client sends it, opens it; the pilot's
     * inventory key opens it.
     */
    @GameTest(template = "tank", timeoutTicks = 60)
    public void itsLockerOpensThroughTheTailHatchAndFromTheSeat(GameTestHelper helper) {
        for (int x = 0; x < LENGTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        // Square on to the world, where the body's own box (a square amidships) stops short of the hatch's after half.
        Submarine s = sub(helper, 24.5, 1, 12.5, 0.0f);
        VehicleProfile p = s.profile();
        ServerPlayer porter = player(helper, "porter", new Vec3(2.5, 1, 2.5));
        helper.runAtTickTime(10, () -> {
            Vec3 store = s.position().add(s.rotate(p.localBlocks(p.storage().orElseThrow().chests().get(0).at())));
            Vec3 aft = s.rotate(new Vec(0.0, 0.0, -0.12));
            Vec3 hatch = store.add(aft).add(0.0, 0.53, 0.0);
            Vec3 across = s.rotate(new Vec(1.0, 0.0, 0.0));
            Vec3 eye = hatch.add(across.scale(1.0)).add(0.0, 1.2, 0.0);
            porter.teleportTo(eye.x, eye.y - porter.getEyeHeight(), eye.z);
            Vec3 inside = hatch.add(hatch.subtract(eye).normalize().scale(0.3));
            Entity target = null;
            Vec3 hit = null;
            List<Entity> boxes = new ArrayList<>(List.of(s.getParts()));
            boxes.add(s);
            for (Entity e : boxes) {
                Optional<Vec3> clip = e.getBoundingBox().clip(porter.getEyePosition(), inside);
                if (clip.isPresent() && (hit == null || clip.get().distanceToSqr(porter.getEyePosition()) < hit.distanceToSqr(porter.getEyePosition()))) {
                    target = e;
                    hit = clip.get();
                }
            }
            helper.assertTrue(target != null, "a hit box covers the hatch");
            porter.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(target, false, InteractionHand.MAIN_HAND, hit.subtract(target.position())));
            helper.assertTrue(porter.containerMenu instanceof ChestMenu m && m.getRowCount() == 1, "the hatch opens one row: " + porter.containerMenu);
            porter.closeContainer();
            helper.assertTrue(porter.startRiding(s, true), "aboard");
            s.openCustomInventoryScreen(porter);
            helper.assertTrue(porter.containerMenu instanceof ChestMenu m && m.getRowCount() == 1, "the pilot's inventory key opens the locker: " + porter.containerMenu);
            logOff(porter);
            helper.succeed();
        });
    }

    @GameTest(template = "tank", timeoutTicks = 120)
    public void aTorpedoLeavesTheChinTubeAheadOfTheNose(GameTestHelper helper) {
        pool(helper, POOL);
        Submarine s = sub(helper, 8.5, 5, 12.5, -90.0f);   // facing +x
        s.fitOut().setItem(s.weaponSlot(0), new ItemStack(SubmersiblesContent.TORPEDO_TUBE.get()));
        s.getItemStacks().set(0, new ItemStack(SubmersiblesContent.TORPEDO_ITEM.get(), 2));
        ServerPlayer pilot = player(helper, "pilot", new Vec3(8.5, 5, 12.5));
        helper.assertTrue(pilot.startRiding(s, true), "at the controls");
        AABB tank = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(LENGTH, HEIGHT, WIDTH)));
        Vec3 ahead = s.rotate(new Vec(0.0, 0.0, 1.0));
        Vec3 left = s.rotate(new Vec(1.0, 0.0, 0.0));
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    s.fire(pilot);
                    s.fire(pilot);
                    List<Torpedo> run = helper.getLevel().getEntitiesOfClass(Torpedo.class, tank);
                    helper.assertValueEqual(run.size(), 1, "one tube: the second press found it reloading");
                    Vec3 d = run.get(0).position().subtract(s.position());
                    helper.assertTrue(d.dot(ahead) > 1.2, "from the chin, ahead of the nose: " + d.dot(ahead));
                    helper.assertTrue(d.y > 0.0 && d.y < 0.4, "low, under the keel: " + d.y);
                    helper.assertTrue(Math.abs(d.dot(left)) < 0.1, "on the centreline: " + d.dot(left));
                })
                .thenIdle(15)
                .thenExecute(() -> {
                    for (Torpedo t : helper.getLevel().getEntitiesOfClass(Torpedo.class, tank)) {
                        helper.assertTrue(t.position().subtract(s.position()).dot(ahead) > 12.0, "running on ahead");
                    }
                    helper.assertValueEqual(s.condition(), Condition.MAX, "its own torpedo never touches it");
                    logOff(pilot);
                })
                .thenSucceed();
    }
}
