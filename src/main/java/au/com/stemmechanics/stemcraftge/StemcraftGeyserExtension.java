package au.com.stemmechanics.stemcraftge;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.entity.property.type.GeyserFloatEntityProperty;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCommandsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.api.util.TriState;
import org.cloudburstmc.math.vector.Vector3f;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class StemcraftGeyserExtension implements Extension {
    static final Identifier ENTITY_ID = Identifier.of("stemcraftge:block_display");
    private static final String PACK_FORMAT_REVISION = "1.0.0-unified-block-overrides";
    private static final List<String> PACK_FILES = List.of(
            "manifest.json",
            "entity/block_display.entity.json",
            "models/entity/block_display.geo.json",
            "animations/block_display.animation.json",
            "render_controllers/block_display.render_controllers.json"
    );
    private boolean configLoaded;
    private float heldItemScale;
    private float rotationX;
    private float rotationY;
    private float rotationZ;
    private float offsetX;
    private float offsetY;
    private float offsetZ;
    private Map<String, Float> blockScaleOverrides = new LinkedHashMap<>();
    private Map<String, Vector3f> blockOffsetOverrides = new LinkedHashMap<>();
    private long packRevision;
    private UUID packUuid;
    private UUID moduleUuid;

    @Subscribe
    public void onDefineEntities(GeyserDefineEntitiesEvent event) {
        loadConfig();
        CustomEntityDefinition definition = CustomEntityDefinition.of(ENTITY_ID);
        event.register(definition);
        CustomBlockDisplayEntity.configure(offsetX, offsetY, offsetZ);
        CustomBlockDisplayEntity.register(definition);
        logger().info("Registered resource-pack-backed block display entity.");
    }

    @Subscribe
    public void onDefineProperties(GeyserDefineEntityPropertiesEvent event) {
        loadConfig();
        CustomBlockDisplayEntity.LEFT_X = property(event, "left_x", -180, 180, 0);
        CustomBlockDisplayEntity.LEFT_Y = property(event, "left_y", -180, 180, 0);
        CustomBlockDisplayEntity.LEFT_Z = property(event, "left_z", -180, 180, 0);
        CustomBlockDisplayEntity.SCALE_X = property(event, "scale_x", -64, 64, 1);
        CustomBlockDisplayEntity.SCALE_Y = property(event, "scale_y", -64, 64, 1);
        CustomBlockDisplayEntity.SCALE_Z = property(event, "scale_z", -64, 64, 1);
        CustomBlockDisplayEntity.RIGHT_X = property(event, "right_x", -180, 180, 0);
        CustomBlockDisplayEntity.RIGHT_Y = property(event, "right_y", -180, 180, 0);
        CustomBlockDisplayEntity.RIGHT_Z = property(event, "right_z", -180, 180, 0);
        CustomBlockDisplayEntity.BASE_SCALE = property(event, "base_scale", 0.01f, 16, heldItemScale);
        CustomBlockDisplayEntity.CAL_X = property(event, "cal_x", -360, 360,
                rotationX - CustomBlockDisplayEntity.DEFAULT_ROTATION_X);
        CustomBlockDisplayEntity.CAL_Y = property(event, "cal_y", -360, 360,
                rotationY - CustomBlockDisplayEntity.DEFAULT_ROTATION_Y);
        CustomBlockDisplayEntity.CAL_Z = property(event, "cal_z", -360, 360,
                rotationZ - CustomBlockDisplayEntity.DEFAULT_ROTATION_Z);
        CustomBlockDisplayEntity.SYNC_REVISION = property(event, "sync_revision", 0, 1, 0);
        CustomBlockDisplayEntity.BLOCK_SCALE = property(event, "block_scale", 0.01f, 16, 1);
    }

    @Subscribe
    public void onDefineCommands(GeyserDefineCommandsEvent event) {
        event.register(Command.<CommandSource>builder(this)
                .source(CommandSource.class)
                .name("calibrate")
                .description("Live block-display calibration")
                .permission("stemcraftge.command.calibrate", TriState.TRUE)
                .playerOnly(false)
                .bedrockOnly(false)
                .executor((source, command, args) -> executeCalibration(source, args))
                .build());
    }

    @Subscribe
    public void onDefineResourcePacks(GeyserDefineResourcePacksEvent event) {
        try {
            loadConfig();
            Path pack = buildPack();
            event.register(ResourcePack.create(PackCodec.path(pack)));
            logger().info("Loaded bundled block-display resource pack.");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to build bundled block-display resource pack", exception);
        }
    }

    private GeyserFloatEntityProperty property(GeyserDefineEntityPropertiesEvent event,
                                                String name, float min, float max, float initial) {
        return event.registerFloatProperty(ENTITY_ID, Identifier.of("stemcraftge:" + name), min, max, initial);
    }

    private Path buildPack() throws IOException {
        Files.createDirectories(dataFolder());
        Path target = dataFolder().resolve("GeyserBlockDisplays.mcpack");
        Path temporary = dataFolder().resolve("GeyserBlockDisplays.mcpack.tmp");

        try (OutputStream output = Files.newOutputStream(temporary);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String file : PACK_FILES) {
                String resourceName = "bedrock_pack/" + file;
                try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                    if (input == null) {
                        throw new IOException("Missing embedded resource " + resourceName);
                    }
                    zip.putNextEntry(new ZipEntry(file));
                    String content = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                            .replace("@HELD_ITEM_SCALE@", Float.toString(heldItemScale))
                            .replace("@ROTATION_X@", Float.toString(rotationX))
                            .replace("@ROTATION_Y@", Float.toString(rotationY))
                            .replace("@ROTATION_Z@", Float.toString(rotationZ))
                            .replace("@PACK_UUID@", packUuid.toString())
                            .replace("@MODULE_UUID@", moduleUuid.toString());
                    zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    private synchronized void loadConfig() {
        if (configLoaded) {
            return;
        }
        try {
            Files.createDirectories(dataFolder());
            Path configPath = dataFolder().resolve("config.yml");
            if (Files.notExists(configPath)) {
                Path legacyPath = dataFolder().resolve("config.properties");
                if (Files.exists(legacyPath)) {
                    importLegacyConfig(legacyPath);
                } else {
                    try (InputStream defaults = getClass().getClassLoader()
                            .getResourceAsStream("default-config.yml")) {
                        if (defaults == null) throw new IOException("Missing default-config.yml");
                        Files.copy(defaults, configPath);
                    }
                }
            }
            readYamlConfig(configPath);
            CRC32 checksum = new CRC32();
            String calibration = heldItemScale + ":" + rotationX + ":" + rotationY + ":"
                    + rotationZ + ":" + offsetX + ":" + offsetY + ":" + offsetZ;
            checksum.update(calibration.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            packRevision = checksum.getValue() % 2_000_000_000L;
            packUuid = UUID.nameUUIDFromBytes(("stemcraftge:pack:" + PACK_FORMAT_REVISION)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            moduleUuid = UUID.nameUUIDFromBytes(("stemcraftge:module:" + PACK_FORMAT_REVISION)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            configLoaded = true;
            applyCalibration();
            logger().info("Calibration from %s: scale=%s rotation=[%s,%s,%s] offset=[%s,%s,%s], pack revision=%s"
                    .formatted(configPath.toAbsolutePath(), heldItemScale, rotationX, rotationY, rotationZ,
                            offsetX, offsetY, offsetZ, packRevision));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to load STEMCraftGE calibration config", exception);
        }
    }

    private void executeCalibration(CommandSource source, String[] args) {
        loadConfig();
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            source.sendMessage(status());
            source.sendMessage("Use: /stemcraftge calibrate add <scale|rx|ry|rz|x|y|z> <amount>");
            source.sendMessage("Or:  /stemcraftge calibrate set <scale|rx|ry|rz|x|y|z> <value>");
            source.sendMessage("Then: /stemcraftge calibrate save (or reload/reset)");
            return;
        }
        if (args[0].equalsIgnoreCase("debug")) {
            source.sendMessage("Configured overrides: " + blockScaleOverrides);
            CustomBlockDisplayEntity.scaleDiagnostics().forEach(source::sendMessage);
            return;
        }
        if (args[0].equalsIgnoreCase("test")) {
            if (args.length != 2) {
                source.sendMessage("Usage: /stemcraftge calibrate test <minecraft:block>");
                return;
            }
            var connection = source.connection();
            if (connection == null) {
                source.sendMessage("The test rig must be created by a connected Bedrock player.");
                return;
            }
            String block = args[1].toLowerCase(java.util.Locale.ROOT);
            if (!block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                source.sendMessage("Invalid block identifier; use a value such as minecraft:chest.");
                return;
            }
            connection.sendCommand("execute at @s align xyz run setblock ~3 ~ ~ minecraft:diamond_block");
            connection.sendCommand("execute at @s align xyz run summon minecraft:block_display ~3 ~ ~ "
                    + "{Tags:[\"stemcraftge_calibration\"],block_state:{Name:\"" + block + "\"}}");
            source.sendMessage("Created " + block + " display over a diamond reference block 3 blocks east."
                    + " Remove displays with: /kill @e[type=minecraft:block_display,tag=stemcraftge_calibration]");
            return;
        }
        if (args[0].equalsIgnoreCase("block")) {
            executeBlockCalibration(source, args);
            return;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            try {
                reloadConfig();
                source.sendMessage("Reloaded and applied config.properties. " + status());
            } catch (IOException | IllegalArgumentException exception) {
                source.sendMessage("Could not reload calibration: " + exception.getMessage());
                logger().error("Could not reload calibration", exception);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("save")) {
            try {
                saveConfig();
                source.sendMessage("STEMCraftGE calibration saved. " + status());
            } catch (IOException exception) {
                source.sendMessage("Could not save calibration: " + exception.getMessage());
                logger().error("Could not save calibration", exception);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("reset")) {
            heldItemScale = 2.7f;
            rotationX = -20f;
            rotationY = -135f;
            rotationZ = 0f;
            offsetX = 0.25f;
            offsetY = 0.575f;
            offsetZ = 0.25f;
            blockScaleOverrides = defaultBlockScaleOverrides();
            blockOffsetOverrides = defaultBlockOffsetOverrides();
            applyCalibration();
            source.sendMessage("Reset live values (not saved). " + status());
            return;
        }
        if (args.length != 3 || !(args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add"))) {
            source.sendMessage("Usage: /stemcraftge calibrate <status|debug|test|block|set|add|save|reload|reset>");
            return;
        }
        try {
            float entered = Float.parseFloat(args[2]);
            if (!Float.isFinite(entered)) throw new NumberFormatException();
            boolean add = args[0].equalsIgnoreCase("add");
            setValue(args[1].toLowerCase(java.util.Locale.ROOT), entered, add);
            applyCalibration();
            source.sendMessage("Applied live. " + status());
        } catch (NumberFormatException exception) {
            source.sendMessage("Value must be a finite number.");
        } catch (IllegalArgumentException exception) {
            source.sendMessage(exception.getMessage());
        }
    }

    private void executeBlockCalibration(CommandSource source, String[] args) {
        if (args.length < 3) {
            source.sendMessage("Usage: /stemcraftge calibrate block <minecraft:block> <status|set|add|clear>");
            return;
        }
        String identifier = args[1].toLowerCase(java.util.Locale.ROOT);
        if (!identifier.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            source.sendMessage("Invalid block identifier; use a value such as minecraft:chest.");
            return;
        }
        String action = args[2].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("status")) {
            source.sendMessage(blockStatus(identifier));
            return;
        }
        if (action.equals("clear")) {
            blockScaleOverrides.remove(identifier);
            blockOffsetOverrides.remove(identifier);
            applyCalibration();
            source.sendMessage("Cleared live override (not saved). " + blockStatus(identifier));
            return;
        }
        if (args.length != 5 || !(action.equals("set") || action.equals("add"))) {
            source.sendMessage("Usage: /stemcraftge calibrate block <block> <set|add> <scale|x|y|z> <value>");
            return;
        }
        try {
            float value = round3(Float.parseFloat(args[4]));
            if (!Float.isFinite(value)) throw new NumberFormatException();
            boolean add = action.equals("add");
            String field = args[3].toLowerCase(java.util.Locale.ROOT);
            if (field.equals("scale")) {
                float current = blockScaleOverrides.getOrDefault(identifier, 1f);
                float updated = round3(add ? current + value : value);
                if (updated <= 0 || updated > 16) {
                    throw new IllegalArgumentException("Block scale must be between 0 and 16.");
                }
                blockScaleOverrides.put(identifier, updated);
            } else if (field.equals("x") || field.equals("y") || field.equals("z")) {
                Vector3f current = blockOffsetOverrides.getOrDefault(identifier, Vector3f.ZERO);
                float x = current.getX(), y = current.getY(), z = current.getZ();
                switch (field) {
                    case "x" -> x = add ? x + value : value;
                    case "y" -> y = add ? y + value : value;
                    case "z" -> z = add ? z + value : value;
                }
                blockOffsetOverrides.put(identifier, Vector3f.from(round3(x), round3(y), round3(z)));
            } else {
                throw new IllegalArgumentException("Unknown field. Use scale, x, y, or z.");
            }
            applyCalibration();
            source.sendMessage("Applied live (not saved). " + blockStatus(identifier));
        } catch (NumberFormatException exception) {
            source.sendMessage("Value must be a finite number.");
        } catch (IllegalArgumentException exception) {
            source.sendMessage(exception.getMessage());
        }
    }

    private String blockStatus(String identifier) {
        float scale = blockScaleOverrides.getOrDefault(identifier, 1f);
        Vector3f offset = blockOffsetOverrides.getOrDefault(identifier, Vector3f.ZERO);
        return "%s scale=%s effective-scale=%s offset=[%s,%s,%s]".formatted(identifier,
                scale, heldItemScale * scale, offset.getX(), offset.getY(), offset.getZ());
    }

    private void setValue(String key, float value, boolean add) {
        switch (key) {
            case "scale" -> heldItemScale = checkedScale(round3(add ? heldItemScale + value : value));
            case "rx" -> rotationX = round3(add ? rotationX + value : value);
            case "ry" -> rotationY = round3(add ? rotationY + value : value);
            case "rz" -> rotationZ = round3(add ? rotationZ + value : value);
            case "x" -> offsetX = round3(add ? offsetX + value : value);
            case "y" -> offsetY = round3(add ? offsetY + value : value);
            case "z" -> offsetZ = round3(add ? offsetZ + value : value);
            default -> throw new IllegalArgumentException("Unknown field. Use scale, rx, ry, rz, x, y, or z.");
        }
    }

    private float checkedScale(float value) {
        if (value < 0.01f || value > 16f) {
            throw new IllegalArgumentException("Scale must be between 0.01 and 16.");
        }
        return value;
    }

    private void applyCalibration() {
        CustomBlockDisplayEntity.applyCalibration(heldItemScale, rotationX, rotationY, rotationZ,
                offsetX, offsetY, offsetZ, blockScaleOverrides, blockOffsetOverrides);
    }

    private String status() {
        return "scale=%s rotation=[%s,%s,%s] offset=[%s,%s,%s] block-overrides=%s".formatted(
                heldItemScale, rotationX, rotationY, rotationZ, offsetX, offsetY, offsetZ,
                blockScaleOverrides.size());
    }

    private void saveConfig() throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("held-item-scale", round3(heldItemScale));
        root.put("rotation", axisMap(rotationX, rotationY, rotationZ));
        root.put("offset", axisMap(offsetX, offsetY, offsetZ));
        Map<String, Object> overrides = new LinkedHashMap<>();
        java.util.LinkedHashSet<String> identifiers = new java.util.LinkedHashSet<>(blockScaleOverrides.keySet());
        identifiers.addAll(blockOffsetOverrides.keySet());
        for (String identifier : identifiers) {
            Map<String, Object> values = new LinkedHashMap<>();
            if (blockScaleOverrides.containsKey(identifier)) {
                values.put("scale", round3(blockScaleOverrides.get(identifier)));
            }
            Vector3f offset = blockOffsetOverrides.get(identifier);
            if (offset != null) {
                values.put("x", round3(offset.getX()));
                values.put("y", round3(offset.getY()));
                values.put("z", round3(offset.getZ()));
            }
            overrides.put(identifier, values);
        }
        root.put("block-overrides", overrides);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        try (OutputStream output = Files.newOutputStream(dataFolder().resolve("config.yml"))) {
            new Yaml(options).dump(root, new java.io.OutputStreamWriter(output,
                    java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private synchronized void reloadConfig() throws IOException {
        Path configPath = dataFolder().resolve("config.yml");
        readYamlConfig(configPath);
        applyCalibration();
        logger().info("Reloaded calibration from %s: %s"
                .formatted(configPath.toAbsolutePath(), status()));
    }

    @SuppressWarnings("unchecked")
    private void readYamlConfig(Path path) throws IOException {
        Map<String, Object> root;
        try (InputStream input = Files.newInputStream(path)) {
            Object loaded = new Yaml().load(input);
            root = loaded instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        }
        heldItemScale = yamlPositive(root.get("held-item-scale"), "held-item-scale", 2.7f);
        Map<String, Object> rotation = childMap(root.get("rotation"));
        rotationX = yamlNumber(rotation.get("x"), "rotation.x", -20f);
        rotationY = yamlNumber(rotation.get("y"), "rotation.y", -135f);
        rotationZ = yamlNumber(rotation.get("z"), "rotation.z", 0f);
        Map<String, Object> offset = childMap(root.get("offset"));
        offsetX = yamlNumber(offset.get("x"), "offset.x", 0.25f);
        offsetY = yamlNumber(offset.get("y"), "offset.y", 0.575f);
        offsetZ = yamlNumber(offset.get("z"), "offset.z", 0.25f);
        blockScaleOverrides = new LinkedHashMap<>();
        blockOffsetOverrides = new LinkedHashMap<>();
        Map<String, Object> unified = childMap(root.get("block-overrides"));
        if (!unified.isEmpty() || root.containsKey("block-overrides")) {
            for (Map.Entry<String, Object> entry : unified.entrySet()) {
                Map<String, Object> values = childMap(entry.getValue());
                String prefix = "block-overrides." + entry.getKey();
                if (values.containsKey("scale")) blockScaleOverrides.put(entry.getKey(),
                        yamlPositive(values.get("scale"), prefix + ".scale", 1f));
                float x = yamlNumber(values.get("x"), prefix + ".x", 0f);
                float y = yamlNumber(values.get("y"), prefix + ".y", 0f);
                float z = yamlNumber(values.get("z"), prefix + ".z", 0f);
                if (values.containsKey("x") || values.containsKey("y") || values.containsKey("z")) {
                    blockOffsetOverrides.put(entry.getKey(), Vector3f.from(x, y, z));
                }
            }
        } else {
            for (Map.Entry<String, Object> entry : childMap(root.get("block-scale-overrides")).entrySet()) {
                blockScaleOverrides.put(entry.getKey(), yamlPositive(entry.getValue(),
                        "block-scale-overrides." + entry.getKey(), 1f));
            }
            for (Map.Entry<String, Object> entry : childMap(root.get("block-offset-overrides")).entrySet()) {
            Map<String, Object> axes = childMap(entry.getValue());
            String prefix = "block-offset-overrides." + entry.getKey();
            blockOffsetOverrides.put(entry.getKey(), Vector3f.from(
                    yamlNumber(axes.get("x"), prefix + ".x", 0f),
                    yamlNumber(axes.get("y"), prefix + ".y", 0f),
                    yamlNumber(axes.get("z"), prefix + ".z", 0f)));
            }
        }
    }

    private void importLegacyConfig(Path legacyPath) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(legacyPath)) {
            properties.load(input);
        }
        heldItemScale = positive(properties, "held-item-scale", 2.7f);
        rotationX = number(properties, "rotation-x", -20f);
        rotationY = number(properties, "rotation-y", -135f);
        rotationZ = number(properties, "rotation-z", 0f);
        offsetX = number(properties, "offset-x", 0.25f);
        offsetY = number(properties, "offset-y", 0.575f);
        offsetZ = number(properties, "offset-z", 0.25f);
        blockScaleOverrides = readBlockScaleOverrides(properties);
        blockOffsetOverrides = defaultBlockOffsetOverrides();
        saveConfig();
        logger().info("Imported legacy config.properties into config.yml");
    }

    private Map<String, Object> axisMap(float x, float y, float z) {
        Map<String, Object> axes = new LinkedHashMap<>();
        axes.put("x", round3(x)); axes.put("y", round3(y)); axes.put("z", round3(z));
        return axes;
    }

    private float round3(float value) {
        return Math.round(value * 1000f) / 1000f;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> childMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private float yamlNumber(Object raw, String key, float fallback) {
        if (raw == null) return fallback;
        if (!(raw instanceof Number number)) throw new IllegalArgumentException(key + " must be a number");
        float value = number.floatValue();
        if (!Float.isFinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }

    private float yamlPositive(Object raw, String key, float fallback) {
        float value = yamlNumber(raw, key, fallback);
        if (value <= 0 || value > 16) throw new IllegalArgumentException(key + " must be between 0 and 16");
        return value;
    }

    private Map<String, Float> readBlockScaleOverrides(Properties properties) {
        Map<String, Float> overrides = defaultBlockScaleOverrides();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("block-scale.")) {
                continue;
            }
            String path = key.substring("block-scale.".length());
            int namespaceSeparator = path.indexOf('.');
            if (namespaceSeparator <= 0 || namespaceSeparator == path.length() - 1) {
                throw new IllegalArgumentException("Invalid block scale key: " + key
                        + " (expected block-scale.minecraft.chest)");
            }
            String identifier = path.substring(0, namespaceSeparator) + ":"
                    + path.substring(namespaceSeparator + 1);
            float multiplier = positive(properties, key, 1f);
            if (multiplier > 16f) {
                throw new IllegalArgumentException(key + " must be no greater than 16");
            }
            overrides.put(identifier, multiplier);
        }
        return overrides;
    }

    private Map<String, Float> defaultBlockScaleOverrides() {
        Map<String, Float> defaults = new LinkedHashMap<>();
        defaults.put("minecraft:chest", 2.6f);
        defaults.put("minecraft:trapped_chest", 2.6f);
        defaults.put("minecraft:ender_chest", 2.6f);
        return defaults;
    }

    private Map<String, Vector3f> defaultBlockOffsetOverrides() {
        Map<String, Vector3f> defaults = new LinkedHashMap<>();
        Vector3f chest = Vector3f.from(-0.4f, 0.1f, 0.9f);
        defaults.put("minecraft:chest", chest);
        defaults.put("minecraft:trapped_chest", chest);
        defaults.put("minecraft:ender_chest", chest);
        return defaults;
    }

    private float positive(Properties properties, String key, float fallback) {
        float value = number(properties, key, fallback);
        if (!Float.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero");
        }
        return value;
    }

    private float number(Properties properties, String key, float fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        float value = Float.parseFloat(raw.trim());
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(key + " must be finite");
        }
        return value;
    }
}
