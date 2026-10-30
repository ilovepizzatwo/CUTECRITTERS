package com.cutecritters.entity;

import com.cutecritters.Bond;
import com.cutecritters.FearUtil;
import com.cutecritters.item.ModItems;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Base for all the cute critters. They are tamed with a Critter Cookie, follow their owner,
 * can sit, can breed, and bring gifts. The higher the owner's bond, the stranger they act:
 * from bond 6 they whisper, from bond 12 they look wrong and sometimes stare at the owner.
 */
public abstract class CritterEntity extends TamableAnimal {
    public static final int WHISPER_BOND = 6;
    public static final int CREEPY_BOND = 12;

    private static final EntityDataAccessor<Boolean> DATA_CREEPY =
            SynchedEntityData.defineId(CritterEntity.class, EntityDataSerializers.BOOLEAN);

    private int giftTimer;
    private int whisperTimer;

    protected CritterEntity(EntityType<? extends CritterEntity> type, Level level) {
        super(type, level);
        this.giftTimer = 20 * (40 + this.random.nextInt(50));
        this.whisperTimer = 20 * (30 + this.random.nextInt(40));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CREEPY, false);
    }

    public boolean isCreepy() {
        return this.entityData.get(DATA_CREEPY);
    }

    protected void setCreepy(boolean value) {
        this.entityData.set(DATA_CREEPY, value);
    }

    // ------------------------------------------------------------------ goals

    protected boolean flies() {
        return false;
    }

    protected Goal createWanderGoal() {
        return new WaterAvoidingRandomStrollGoal(this, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new StareGoal(this));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.15D, 7.0F, 2.0F, this.flies()));
        this.goalSelector.addGoal(4, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.1D, Ingredient.of(ModItems.CRITTER_COOKIE.get()), false));
        this.goalSelector.addGoal(6, this.createWanderGoal());
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ModItems.CRITTER_COOKIE.get());
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!this.isTame()) {
            if (!this.isFood(stack)) {
                return InteractionResult.PASS;
            }
            if (!this.level().isClientSide) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                if (this.random.nextInt(3) == 0) {
                    this.tame(player);
                    if (player instanceof ServerPlayer serverPlayer) {
                        CriteriaTriggers.TAME_ANIMAL.trigger(serverPlayer, this);
                    }
                    this.navigation.stop();
                    this.setTarget(null);
                    this.setOrderedToSit(false);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (this.isOwnedBy(player) && !this.isFood(stack)
                && hand == InteractionHand.MAIN_HAND && stack.isEmpty()) {
            if (!this.level().isClientSide) {
                this.setOrderedToSit(!this.isOrderedToSit());
                this.jumping = false;
                this.navigation.stop();
                this.setTarget(null);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Entity child = this.getType().create(level);
        if (child instanceof CritterEntity critter) {
            UUID owner = this.getOwnerUUID();
            if (owner != null) {
                critter.setOwnerUUID(owner);
                critter.setTame(true);
            }
            return critter;
        }
        return null;
    }

    // ------------------------------------------------------------------ behaviour that depends on the bond

    protected void ability(ServerPlayer owner, int bond) {
    }

    protected abstract ItemStack makeGift(ServerPlayer owner, int bond);

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.tickCount % 20 != 0) {
            return;
        }
        this.secondTick();
    }

    private void secondTick() {
        LivingEntity ownerEntity = this.getOwner();
        if (!this.isTame() || !(ownerEntity instanceof ServerPlayer owner)) {
            if (this.isCreepy()) {
                this.setCreepy(false);
            }
            return;
        }

        int bond = Bond.get(owner);
        boolean creepy = bond >= CREEPY_BOND;
        if (creepy != this.isCreepy()) {
            this.setCreepy(creepy);
        }

        if (this.distanceToSqr(owner) > 256.0D) {
            return;
        }

        this.ability(owner, bond);

        if (bond >= WHISPER_BOND && --this.whisperTimer <= 0) {
            this.whisperTimer = 20 * (35 + this.random.nextInt(60));
            Vec3 pos = this.position();
            FearUtil.playAt(owner, creepy ? SoundEvents.WARDEN_NEARBY_CLOSE : SoundEvents.AMBIENT_CAVE.value(),
                    pos, 0.35F, 0.5F + this.random.nextFloat() * 0.3F);
        }

        if (!this.isOrderedToSit() && --this.giftTimer <= 0) {
            this.giftTimer = 20 * (90 + this.random.nextInt(90));
            this.giveGift(owner, bond, creepy);
        }
    }

    private void giveGift(ServerPlayer owner, int bond, boolean creepy) {
        ItemStack gift = this.makeGift(owner, bond);
        if (creepy && this.random.nextInt(3) == 0) {
            gift = switch (this.random.nextInt(3)) {
                case 0 -> new ItemStack(Items.BONE);
                case 1 -> new ItemStack(Items.ROTTEN_FLESH);
                default -> new ItemStack(Items.SPIDER_EYE);
            };
        }
        if (gift.isEmpty()) {
            return;
        }
        this.spawnAtLocation(gift, 0.3F);
        this.level().broadcastEntityEvent(this, (byte) 7);
    }

    // ------------------------------------------------------------------ the stare

    /** At a high bond the critter sometimes freezes and just looks at its owner. */
    private static class StareGoal extends Goal {
        private final CritterEntity critter;
        private int ticks;

        StareGoal(CritterEntity critter) {
            this.critter = critter;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.critter.getOwner();
            return this.critter.isTame() && this.critter.isCreepy() && !this.critter.isOrderedToSit()
                    && owner != null && this.critter.distanceToSqr(owner) < 256.0D
                    && this.critter.getRandom().nextInt(500) == 0;
        }

        @Override
        public void start() {
            this.ticks = 100 + this.critter.getRandom().nextInt(80);
            this.critter.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse() {
            return this.ticks > 0 && this.critter.getOwner() != null;
        }

        @Override
        public void tick() {
            this.ticks--;
            LivingEntity owner = this.critter.getOwner();
            if (owner != null) {
                this.critter.getLookControl().setLookAt(owner, 60.0F, 60.0F);
            }
        }
    }
}
