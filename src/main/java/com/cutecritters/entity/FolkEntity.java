package com.cutecritters.entity;

import com.cutecritters.Bond;
import com.cutecritters.FearUtil;
import com.cutecritters.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A resident of the valley. Five kinds (the Variant tag):
 * 0 baker, 1 gardener, 2 child, 3 sexton (graveyard keeper), 4 librarian.
 * Right click to talk. Hold a cookie to swap it for a small gift.
 * The more bonded the player is, the stranger the lines become.
 */
public class FolkEntity extends PathfinderMob {
    public static final int KINDS = 5;
    public static final int SEXTON = 3;
    private static final int LINES = 3;
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(FolkEntity.class, EntityDataSerializers.INT);

    private int tradeCooldown;
    private int turnTimer;

    public FolkEntity(EntityType<? extends FolkEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(VARIANT, 0);
    }

    public int getVariant() {
        return Math.floorMod(this.entityData.get(VARIANT), KINDS);
    }

    public void setVariant(int variant) {
        this.entityData.set(VARIANT, Math.floorMod(variant, KINDS));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.getVariant());
        tag.putInt("TradeCooldown", this.tradeCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setVariant(tag.getInt("Variant"));
        this.tradeCooldown = tag.getInt("TradeCooldown");
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("folk.cutecritters.name." + this.getVariant());
    }

    @Override
    protected void registerGoals() {
        // The variant is not loaded yet when goals are registered, so the sexton check lives in canUse().
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.6D) {
            @Override
            public boolean canUse() {
                return FolkEntity.this.getVariant() != SEXTON && super.canUse();
            }
        });
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D, 0.004F) {
            @Override
            public boolean canUse() {
                return FolkEntity.this.getVariant() != SEXTON && super.canUse();
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F) {
            @Override
            public boolean canUse() {
                return FolkEntity.this.getVariant() != SEXTON && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return FolkEntity.this.getVariant() != SEXTON && super.canUse();
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.tradeCooldown > 0) {
            this.tradeCooldown--;
        }
        if (!this.hasRestriction()) {
            this.restrictTo(this.blockPosition(), 16);
        }
        if (this.getVariant() == SEXTON && ++this.turnTimer >= 8) {
            this.turnTimer = 0;
            this.sextonTurn();
        }
    }

    /** The sexton stands perfectly still while someone watches him and turns toward whoever looks away. */
    private void sextonTurn() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player nearest = serverLevel.getNearestPlayer(this, 20.0D);
        if (!(nearest instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (FearUtil.isLookingAt(player, this, 0.88D)) {
            return;
        }
        double dx = player.getX() - this.getX();
        double dz = player.getZ() - this.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setYHeadRot(yaw);
        this.setYBodyRot(yaw);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.CRITTER_COOKIE.get())) {
            this.trade(player, held);
        } else {
            this.talk(player);
        }
        return InteractionResult.sidedSuccess(false);
    }

    private void say(Player player, Component line) {
        player.sendSystemMessage(Component.translatable("folk.cutecritters.say", this.getName(), line)
                .withStyle(ChatFormatting.WHITE));
    }

    private void talk(Player player) {
        int variant = this.getVariant();
        boolean dark = Bond.get(player) >= 6;
        String key = "folk.cutecritters." + variant + (dark ? ".dark." : ".") + this.random.nextInt(LINES);
        this.say(player, Component.translatable(key).withStyle(dark ? ChatFormatting.GRAY : ChatFormatting.WHITE));
        if (!dark && variant != SEXTON) {
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    this.getX(), this.getY() + 2.0D, this.getZ(), 3, 0.3D, 0.2D, 0.3D, 0.0D);
        }
    }

    private void trade(Player player, ItemStack cookie) {
        int variant = this.getVariant();
        if (this.tradeCooldown > 0) {
            this.say(player, Component.translatable("folk.cutecritters.later"));
            return;
        }
        if (!player.getAbilities().instabuild) {
            cookie.shrink(1);
        }
        this.tradeCooldown = 20 * 45;
        ItemStack reward = switch (variant) {
            case 0 -> new ItemStack(Items.BREAD, 3);
            case 1 -> new ItemStack(Items.SWEET_BERRIES, 4);
            case 2 -> new ItemStack(Items.COOKIE, 2);
            case SEXTON -> new ItemStack(Items.WHITE_CANDLE, 2);
            default -> new ItemStack(Items.PAPER, 4);
        };
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        this.say(player, Component.translatable("folk.cutecritters.thanks." + variant));
        ((ServerLevel) this.level()).sendParticles(ParticleTypes.HEART,
                this.getX(), this.getY() + 2.0D, this.getZ(), 4, 0.3D, 0.2D, 0.3D, 0.0D);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.getVariant() == SEXTON ? null : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }
}
