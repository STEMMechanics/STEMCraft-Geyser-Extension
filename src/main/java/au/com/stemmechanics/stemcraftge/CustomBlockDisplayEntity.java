package au.com.stemmechanics.stemcraftge;

import org.cloudburstmc.math.imaginary.Quaternionf;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.entity.property.type.GeyserFloatEntityProperty;
import org.geysermc.geyser.entity.EntityTypeBase;
import org.geysermc.geyser.entity.EntityTypeDefinition;
import org.geysermc.geyser.entity.VanillaEntityType;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

import java.util.Collections;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;

/** A Java block display backed by a purpose-built Bedrock custom entity. */
public final class CustomBlockDisplayEntity extends LivingEntity {
    static final float DEFAULT_ROTATION_X = -20f;
    static final float DEFAULT_ROTATION_Y = -135f;
    static final float DEFAULT_ROTATION_Z = 0f;
    private static Vector3f bedrockOffset = Vector3f.ZERO;
    private static final Set<CustomBlockDisplayEntity> LIVE =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static float baseScale = 2.6f;
    private static Vector3f calibrationRotation = Vector3f.from(
            DEFAULT_ROTATION_X, DEFAULT_ROTATION_Y, DEFAULT_ROTATION_Z);
    private static Map<String, Float> blockScaleOverrides = Map.of();
    private static Map<String, Vector3f> blockOffsetOverrides = Map.of();
    static GeyserFloatEntityProperty LEFT_X;
    static GeyserFloatEntityProperty LEFT_Y;
    static GeyserFloatEntityProperty LEFT_Z;
    static GeyserFloatEntityProperty SCALE_X;
    static GeyserFloatEntityProperty SCALE_Y;
    static GeyserFloatEntityProperty SCALE_Z;
    static GeyserFloatEntityProperty RIGHT_X;
    static GeyserFloatEntityProperty RIGHT_Y;
    static GeyserFloatEntityProperty RIGHT_Z;
    static GeyserFloatEntityProperty BASE_SCALE;
    static GeyserFloatEntityProperty CAL_X;
    static GeyserFloatEntityProperty CAL_Y;
    static GeyserFloatEntityProperty CAL_Z;
    static GeyserFloatEntityProperty SYNC_REVISION;
    static GeyserFloatEntityProperty BLOCK_SCALE;

    private Vector3f translation = Vector3f.ZERO;
    private Vector3f displayScale = Vector3f.ONE;
    private Quaternionf leftRotation = identity();
    private Quaternionf rightRotation = identity();
    private float syncRevision;
    private String blockIdentifier = "minecraft:air";

    private CustomBlockDisplayEntity(EntitySpawnContext context) {
        super(context);
        LIVE.add(this);
    }

    public static void configure(float offsetX, float offsetY, float offsetZ) {
        bedrockOffset = Vector3f.from(offsetX, offsetY, offsetZ);
    }

    public static void applyCalibration(float scale, float rotationX, float rotationY, float rotationZ,
                                        float offsetX, float offsetY, float offsetZ,
                                        Map<String, Float> scaleOverrides,
                                        Map<String, Vector3f> offsetOverrides) {
        baseScale = scale;
        calibrationRotation = Vector3f.from(rotationX, rotationY, rotationZ);
        bedrockOffset = Vector3f.from(offsetX, offsetY, offsetZ);
        blockScaleOverrides = Map.copyOf(scaleOverrides);
        blockOffsetOverrides = Map.copyOf(offsetOverrides);
        synchronized (LIVE) {
            for (CustomBlockDisplayEntity entity : LIVE) {
                entity.toggleCalibrationSync();
                if (entity.isValid()) {
                    entity.moveAbsoluteRaw(entity.position, entity.yaw, entity.pitch,
                            entity.headYaw, entity.onGround, true);
                }
            }
        }
    }

    public static List<String> scaleDiagnostics() {
        Map<String, Integer> counts = new TreeMap<>();
        synchronized (LIVE) {
            for (CustomBlockDisplayEntity entity : LIVE) {
                if (entity.isValid()) {
                    counts.merge(entity.blockIdentifier, 1, Integer::sum);
                }
            }
        }
        List<String> lines = new ArrayList<>();
        counts.forEach((identifier, count) -> {
            float multiplier = blockScaleOverrides.getOrDefault(identifier, 1f);
            lines.add(identifier + " x" + count + " multiplier=" + multiplier
                    + " effective-scale=" + (baseScale * multiplier));
        });
        if (lines.isEmpty()) lines.add("No live block displays found.");
        return lines;
    }

    private void toggleCalibrationSync() {
        if (!isValid() || SYNC_REVISION == null) {
            return;
        }
        syncRevision = syncRevision == 0f ? 1f : 0f;
        syncTransform(true);
    }

    private void scheduleCalibrationSync() {
        scheduleFullTransformSync(500);
        scheduleFullTransformSync(1500);
        scheduleFullTransformSync(3000);
    }

    private void scheduleFullTransformSync(long delayMillis) {
        session.scheduleInEventLoop(() -> {
            if (!isValid()) {
                return;
            }
            // World/chunk entity metadata can arrive after the Bedrock spawn packet.
            // Resend the complete Java transform, not only the calibration values.
            toggleCalibrationSync();
            moveAbsoluteRaw(position, yaw, pitch, headYaw, onGround, true);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public static void register(CustomEntityDefinition customDefinition) {
        EntityTypeBase<Entity> entityBase = EntityTypeDefinition.baseBuilder(Entity.class)
                .addTranslator(MetadataTypes.BYTE, Entity::setFlags)
                .addTranslator(MetadataTypes.INT, Entity::setAir)
                .addTranslator(MetadataTypes.OPTIONAL_COMPONENT, Entity::setCustomName)
                .addTranslator(MetadataTypes.BOOLEAN, Entity::setCustomNameVisible)
                .addTranslator(MetadataTypes.BOOLEAN, Entity::setSilent)
                .addTranslator(MetadataTypes.BOOLEAN, Entity::setGravity)
                .addTranslator(MetadataTypes.POSE, (entity, metadata) -> entity.setPose(metadata.getValue()))
                .addTranslator(MetadataTypes.INT, Entity::setFreezing)
                .build();

        EntityTypeBase<CustomBlockDisplayEntity> displayBase =
                EntityTypeBase.baseInherited(CustomBlockDisplayEntity.class, entityBase)
                        .addTranslator(null)
                        .addTranslator(null)
                        .addTranslator(null)
                        .addTranslator(MetadataTypes.VECTOR3, CustomBlockDisplayEntity::setTranslation)
                        .addTranslator(MetadataTypes.VECTOR3, CustomBlockDisplayEntity::setDisplayScale)
                        .addTranslator(MetadataTypes.QUATERNION, CustomBlockDisplayEntity::setLeftRotation)
                        .addTranslator(MetadataTypes.QUATERNION, CustomBlockDisplayEntity::setRightRotation)
                        .addTranslator(null)
                        .addTranslator(null)
                        .addTranslator(null)
                        .addTranslator(null)
                        .addTranslator(null)
                        .addTranslator(null)
                        .addTranslator(null)
                        .addTranslator(null)
                        .build();

        VanillaEntityType.inherited(CustomBlockDisplayEntity::new, displayBase)
                .type(EntityType.BLOCK_DISPLAY)
                .bedrockDefinition((org.geysermc.geyser.entity.BedrockEntityDefinition) customDefinition)
                .heightAndWidth(0)
                .addTranslator(MetadataTypes.BLOCK_STATE, CustomBlockDisplayEntity::setBlock)
                .build();
    }

    @Override
    protected void initializeMetadata() {
        super.initializeMetadata();
        metadata.put(EntityDataTypes.COLLISION_BOX, Vector3f.ZERO);
        metadata.put(EntityDataTypes.WIDTH, 0f);
        metadata.put(EntityDataTypes.HEIGHT, 0f);
        setFlag(EntityFlag.SILENT, true);
        setFlag(EntityFlag.NO_AI, true);
        setFlag(EntityFlag.HAS_COLLISION, false);
        setFlag(EntityFlag.HAS_GRAVITY, false);
        setFlag(EntityFlag.PUSH_TOWARDS_CLOSEST_SPACE, false);
    }

    @Override
    public void spawnEntity() {
        syncTransform(false);
        super.spawnEntity();
        // updatePropertiesBatched may be scheduled onto the session event loop.
        // Send once more after valid=true so startup calibration cannot be lost
        // behind the custom property's registered default values.
        scheduleCalibrationSync();
        updateMainHand();
    }

    @Override
    public Vector3f bedrockPosition() {
        return super.bedrockPosition().add(translation).add(bedrockOffset)
                .add(blockOffsetOverrides.getOrDefault(blockIdentifier, Vector3f.ZERO));
    }

    @Override
    public void tick() {
        // A display has no Bedrock-side movement or living behavior.
    }

    @Override
    public boolean shouldLerp() {
        // LivingEntity normally queues nearby movement and consumes it from tick().
        // Displays intentionally do not tick, so send every Java movement update immediately.
        return false;
    }

    public void setTranslation(EntityMetadata<Vector3f, ?> metadata) {
        translation = metadata.getValue() == null ? Vector3f.ZERO : metadata.getValue();
        if (isValid()) {
            moveAbsoluteRaw(position, yaw, pitch, headYaw, onGround, true);
        }
    }

    public void setDisplayScale(EntityMetadata<Vector3f, ?> metadata) {
        displayScale = metadata.getValue() == null ? Vector3f.ONE : metadata.getValue();
        syncTransform(true);
    }

    public void setLeftRotation(EntityMetadata<Quaternionf, ?> metadata) {
        leftRotation = metadata.getValue() == null ? identity() : metadata.getValue();
        syncTransform(true);
    }

    public void setRightRotation(EntityMetadata<Quaternionf, ?> metadata) {
        rightRotation = metadata.getValue() == null ? identity() : metadata.getValue();
        syncTransform(true);
    }

    public void setBlock(IntEntityMetadata metadata) {
        BlockState state = BlockState.of(metadata.getPrimitiveValue());
        Item item = Item.byBlock(state.block());
        // Block#javaIdentifier changed binary return type between nearby Geyser
        // master builds. Item#javaIdentifier remains a String on both and gives
        // us the same identifier for configurable block-item scale overrides.
        blockIdentifier = item == null ? "minecraft:air" : item.javaIdentifier();
        setHand((item == null ? Items.AIR : item).newItemStack(session, 1, null));
        updateMainHand();
        syncTransform(true);
        if (isValid()) {
            moveAbsoluteRaw(position, yaw, pitch, headYaw, onGround, true);
        }
    }

    private void syncTransform(boolean immediate) {
        if (LEFT_X == null) {
            return;
        }
        Vector3f left = toEulerDegrees(leftRotation);
        Vector3f right = toEulerDegrees(rightRotation);
        updatePropertiesBatched(update -> {
            update.update(LEFT_X, left.getX());
            update.update(LEFT_Y, left.getY());
            update.update(LEFT_Z, left.getZ());
            update.update(SCALE_X, clamp(displayScale.getX(), -64, 64));
            update.update(SCALE_Y, clamp(displayScale.getY(), -64, 64));
            update.update(SCALE_Z, clamp(displayScale.getZ(), -64, 64));
            update.update(RIGHT_X, right.getX());
            update.update(RIGHT_Y, right.getY());
            update.update(RIGHT_Z, right.getZ());
            update.update(BASE_SCALE, baseScale);
            update.update(CAL_X, calibrationRotation.getX() - DEFAULT_ROTATION_X);
            update.update(CAL_Y, calibrationRotation.getY() - DEFAULT_ROTATION_Y);
            update.update(CAL_Z, calibrationRotation.getZ() - DEFAULT_ROTATION_Z);
            update.update(SYNC_REVISION, syncRevision);
            update.update(BLOCK_SCALE, blockScaleOverrides.getOrDefault(blockIdentifier, 1f));
        }, immediate && isValid());
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Quaternionf identity() {
        return Quaternionf.from(0, 0, 0, 1);
    }

    private static Vector3f toEulerDegrees(Quaternionf q) {
        double sinr = 2 * (q.getW() * q.getX() + q.getY() * q.getZ());
        double cosr = 1 - 2 * (q.getX() * q.getX() + q.getY() * q.getY());
        double x = Math.atan2(sinr, cosr);
        double sinp = 2 * (q.getW() * q.getY() - q.getZ() * q.getX());
        double y = Math.abs(sinp) >= 1 ? Math.copySign(Math.PI / 2, sinp) : Math.asin(sinp);
        double siny = 2 * (q.getW() * q.getZ() + q.getX() * q.getY());
        double cosy = 1 - 2 * (q.getY() * q.getY() + q.getZ() * q.getZ());
        double z = Math.atan2(siny, cosy);
        return Vector3f.from(Math.toDegrees(x), Math.toDegrees(y), Math.toDegrees(z));
    }
}
