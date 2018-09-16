package com.example.mod;

import static net.minecraft.command.Commands.argument;
import static net.minecraft.command.Commands.literal;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.LogManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.command.CommandSource;
import net.minecraft.command.arguments.BlockPosArgument;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.gen.feature.template.TemplateManager;

public class StructureCommand
{

    public static final DynamicCommandExceptionType TEMPLATE_INVALID_EXCEPTION = 
        new DynamicCommandExceptionType(template -> new TextComponentTranslation("Template %s doesn't exist", template));
    
    public static void register(CommandDispatcher<CommandSource> dispatcher)
    {
    	ArgumentBuilder<CommandSource, ?> posArgument = argument("pos", BlockPosArgument.blockPos())
                .executes(ctx -> loadStructure(ctx.getSource(),
                        TemplateArgument.getTemplate(ctx, "template"),
                        BlockPosArgument.getBlockPos(ctx, "pos"),
                        Mirror.NONE,
                        Rotation.NONE,
                        true,
                        1,
                        0));
        ArgumentBuilder<CommandSource, ?> load = literal("load")
                .then(argument("template", TemplateArgument.template())
                    .executes(ctx -> loadStructure(ctx.getSource(),
                            TemplateArgument.getTemplate(ctx, "template"),
                            new BlockPos(ctx.getSource().getPos()),
                            Mirror.NONE,
                            Rotation.NONE,
                            true,
                            1,
                            0))
                    .then(posArgument));
        
        createMirrorArgument(posArgument, (mirror, builder1) -> {
            builder1.executes(ctx -> loadStructure(ctx.getSource(),
                    TemplateArgument.getTemplate(ctx, "template"),
                    BlockPosArgument.getBlockPos(ctx, "pos"),
                    mirror,
                    Rotation.NONE,
                    true,
                    1,
                    0));
            createRotationArgument(builder1, (rotation, builder2) -> {
                builder2.executes(ctx -> loadStructure(ctx.getSource(),
                        TemplateArgument.getTemplate(ctx, "template"),
                        BlockPosArgument.getBlockPos(ctx, "pos"),
                        mirror,
                        rotation,
                        true,
                        1,
                        0));
                builder2.then(argument("ignoreEntities", BoolArgumentType.bool())
                    .executes(ctx -> loadStructure(ctx.getSource(),
                            TemplateArgument.getTemplate(ctx, "template"),
                            BlockPosArgument.getBlockPos(ctx, "pos"),
                            mirror,
                            rotation,
                            BoolArgumentType.getBool(ctx, "ignoreEntities"),
                            1,
                            0))
                    .then(argument("integrity", FloatArgumentType.floatArg(0, 1))
                        .executes(ctx -> loadStructure(ctx.getSource(),
                                TemplateArgument.getTemplate(ctx, "template"),
                                BlockPosArgument.getBlockPos(ctx, "pos"),
                                mirror,
                                rotation,
                                BoolArgumentType.getBool(ctx, "ignoreEntities"),
                                FloatArgumentType.getFloat(ctx, "integrity"),
                                new Random().nextLong()))
                        .then(argument("seed", LongArgumentType.longArg())
                            .executes(ctx -> loadStructure(ctx.getSource(),
                                    TemplateArgument.getTemplate(ctx, "template"),
                                    BlockPosArgument.getBlockPos(ctx, "pos"),
                                    mirror,
                                    rotation,
                                    BoolArgumentType.getBool(ctx, "ignoreEntities"),
                                    FloatArgumentType.getFloat(ctx, "integrity"),
                                    LongArgumentType.getLong(ctx, "seed"))))));
            });
        });
        
        ArgumentBuilder<CommandSource, ?> save = literal("save")
                .then(argument("template", TemplateArgument.template())
                    .then(argument("from", BlockPosArgument.blockPos())
                        .then(argument("to", BlockPosArgument.blockPos())
                            .executes(ctx -> saveStructure(ctx.getSource(),
                                    TemplateArgument.getTemplate(ctx, "template"),
                                    BlockPosArgument.getBlockPos(ctx, "from"),
                                    BlockPosArgument.getBlockPos(ctx, "to"),
                                    true))
                            .then(argument("ignoreEntities", BoolArgumentType.bool())
                                .executes(ctx -> saveStructure(ctx.getSource(),
                                        TemplateArgument.getTemplate(ctx, "template"),
                                        BlockPosArgument.getBlockPos(ctx, "from"),
                                        BlockPosArgument.getBlockPos(ctx, "to"),
                                        BoolArgumentType.getBool(ctx, "ignoreEntities")))))));
        
        ArgumentBuilder<CommandSource, ?> list = literal("list")
                .executes(ctx -> listStructures(ctx.getSource(), 1))
                .then(argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> listStructures(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "page"))));
        
        dispatcher.register(literal("structure")
            .requires(source -> source.hasPermissionLevel(2))
            .then(load)
            .then(save)
            .then(list));
    }
    
    private static void createMirrorArgument(ArgumentBuilder<CommandSource, ?> builder, BiConsumer<Mirror, ? super ArgumentBuilder<CommandSource, ?>> then)
    {
        ArgumentBuilder<CommandSource, ?> child;
        
        child = literal("no_mirror");
        then.accept(Mirror.NONE, child);
        builder.then(child);
        
        child = literal("mirror_left_right");
        then.accept(Mirror.LEFT_RIGHT, child);
        builder.then(child);
        
        child = literal("mirror_front_back");
        then.accept(Mirror.FRONT_BACK, child);
        builder.then(child);
    }
    
    private static void createRotationArgument(ArgumentBuilder<CommandSource, ?> builder, BiConsumer<Rotation, ? super ArgumentBuilder<CommandSource, ?>> then)
    {
        ArgumentBuilder<CommandSource, ?> child;
        
        child = literal("rotate_0");
        then.accept(Rotation.NONE, child);
        builder.then(child);
        
        child = literal("rotate_90");
        then.accept(Rotation.CLOCKWISE_90, child);
        builder.then(child);
        
        child = literal("rotate_180");
        then.accept(Rotation.CLOCKWISE_180, child);
        builder.then(child);
        
        child = literal("rotate_270");
        then.accept(Rotation.COUNTERCLOCKWISE_90, child);
        builder.then(child);
    }
    
    private static int loadStructure(CommandSource source, ResourceLocation templateLocation, BlockPos pos, Mirror mirror, Rotation rotation, boolean ignoreEntities, float integrity, long seed) throws CommandSyntaxException
    {
    	TemplateManager manager = source.getServer().worlds[0].getStructureTemplateManager();
    	Template template = manager.getTemplate(templateLocation);
    	if (template == null)
    		throw TEMPLATE_INVALID_EXCEPTION.create(templateLocation);
    	
        PlacementSettings settings = new PlacementSettings().setMirror(mirror).setRotation(rotation).setIgnoreEntities(ignoreEntities).setChunk(null).setReplacedBlock(null).setIgnoreStructureBlock(false);
        if (integrity < 1)
        {
            settings.setIntegrity(integrity).setSeed(seed);
        }
        
        template.addBlocksToWorldChunk(source.getWorld(), pos, settings);
        
        source.sendFeedback(new TextComponentString("Successfully loaded structure"), true);
        
        return 0;
    }
    
    @SuppressWarnings("unchecked")
    private static int saveStructure(CommandSource source, ResourceLocation templateLocation, BlockPos from, BlockPos to, boolean ignoreEntities)
    {
        TemplateManager manager = source.getServer().worlds[0].getStructureTemplateManager();
        Template template = manager.getTemplateDefaulted(templateLocation);
        
        MutableBoundingBox bb = new MutableBoundingBox(from, to);
        BlockPos origin = new BlockPos(bb.minX, bb.minY, bb.minZ);
        BlockPos size = new BlockPos(bb.getXSize(), bb.getYSize(), bb.getZSize());
        
        template.takeBlocksFromWorld(source.getWorld(), origin, size, !ignoreEntities, Blocks.STRUCTURE_VOID);
        template.setAuthor(source.getName());

        Field field;
        try
        {
            field = TemplateManager.class.getDeclaredField("templates");
        }
        catch (NoSuchFieldException e)
        {
            try
            {
                field = TemplateManager.class.getDeclaredField("b");
            }
            catch (NoSuchFieldException e1)
            {
                throw new AssertionError(e);
            }
        }
        field.setAccessible(true);
        Map<ResourceLocation, Template> templates;
        try
        {
            templates = (Map<ResourceLocation, Template>) field.get(manager);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        ResourceLocation rl = null;
        for (Map.Entry<ResourceLocation, Template> entry : templates.entrySet())
        {
            if (entry.getValue() == template)
            {
                rl = entry.getKey();
                break;
            }
        }
        
        manager.writeToFile(rl);
        
        source.sendFeedback(new TextComponentString("Successfully saved structure"), true);
        
        return 0;
    }
    
    private static int listStructures(CommandSource source, int page)
    {
        TemplateManager manager = MinecraftServer.INSTANCE.worlds[0].getStructureTemplateManager();
        List<ResourceLocation> templates = listStructures(manager);
        
        if (templates.isEmpty())
        {
            source.sendFeedback(new TextComponentString("There are no saved structures yet"), false);
        }
        else
        {
            final int PAGE_SIZE = 9;
            int pageCount = (templates.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            page--;
            if (page >= pageCount)
                page = pageCount - 1;
            
            source.sendFeedback(new TextComponentString(TextFormatting.GREEN + "Structure list page " + (page + 1) + " of " + pageCount + " (/structure list <page>)"), false);
            for (int offset = 0; offset < PAGE_SIZE && page * PAGE_SIZE + offset < templates.size(); offset++)
            {
                ResourceLocation template = templates.get(page * PAGE_SIZE + offset);
                source.sendFeedback(new TextComponentString("- " + template + " by " + manager.getTemplate(template).getAuthor()), false);
            }
        }
        
        return templates.size();
    }
    
    public static List<ResourceLocation> listStructures(TemplateManager manager)
    {
        List<ResourceLocation> templates = new ArrayList<>();
        
        Field field;
        try
        {
            field = TemplateManager.class.getDeclaredField("pathGenerated");
        }
        catch (NoSuchFieldException e)
        {
            try
            {
                field = TemplateManager.class.getDeclaredField("e");
            }
            catch (NoSuchFieldException e1)
            {
                throw new AssertionError(e);
            }
        }
        field.setAccessible(true);
        Path templatesPath;
        try
        {
            templatesPath = (Path) field.get(manager);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
        
        try
        {
            Files.list(templatesPath).map(p -> p.resolve("structures")).flatMap(p -> {
                try
                {
                    return Files.walk(p);
                }
                catch (IOException e)
                {
                    LogManager.getLogger().error("Unable to list custom structures", e);
                    return Stream.empty();
                }
            }).map(p -> {
                String s = templatesPath.relativize(p).toString();
                String[] parts = s.split("/", 3);
                return new ResourceLocation(parts[0], parts[2]);
            }).forEach(templates::add);
        }
        catch (IOException e)
        {
            LogManager.getLogger().error("Unable to list custom structures", e);
        }
        
        Pattern pattern = Pattern.compile(".*assets/(\\w+)/structures/([\\w/]+)\\.nbt");
        try
        {
            try
            {
                // try zip file first
                ZipFile zip = new ZipFile(new File(MinecraftServer.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements())
                {
                    String entryName = entries.nextElement().getName();
                    Matcher matcher = pattern.matcher(entryName);
                    if (matcher.matches())
                    {
                        templates.add(new ResourceLocation(matcher.group(1), matcher.group(2)));
                    }
                }
                zip.close();
            }
            catch (Exception e)
            {
                // try folder
                Path root = Paths.get(MinecraftServer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                Files.find(root, Integer.MAX_VALUE,
                        (path, attr) -> attr.isRegularFile() && pattern.matcher(path.toString().replace(File.separator, "/")).matches())
                .forEach(path -> {
                    Matcher matcher = pattern.matcher(path.toString().replace(File.separator, "/"));
                    matcher.matches(); // == true
                    templates.add(new ResourceLocation(matcher.group(1), matcher.group(2)));
                });
            }
        }
        catch (IOException | URISyntaxException e)
        {
            LogManager.getLogger().error("Unable to list built in structures", e);
        }
        
        Collections.sort(templates, Comparator.<ResourceLocation, String>comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        
        return templates;
    }
    
}
