package com.example.mod;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.command.CommandSource;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.gen.feature.template.TemplateManager;

public class TemplateArgument implements ArgumentType<Template>
{
    public static final DynamicCommandExceptionType TEMPLATE_INVALID_EXCEPTION = 
        new DynamicCommandExceptionType(template -> new TextComponentTranslation("Template %s doesn't exist", template));
    
    private final boolean createTemplate;
    
    private TemplateArgument(boolean createTemplate)
    {
        this.createTemplate = createTemplate;
    }

    public static TemplateArgument template(boolean create)
    {
        return new TemplateArgument(create);
    }

    public static Template getTemplate(CommandContext<CommandSource> context, String name)
    {
        return (Template)context.getArgument(name, Template.class);
    }

    public Template parse(StringReader reader) throws CommandSyntaxException
    {
        String structureName = reader.readString();
        TemplateManager manager = MinecraftServer.INSTANCE.worlds[0].getStructureTemplateManager();
        Template template;
        if (createTemplate)
        {
            template = manager.getTemplateDefaulted(new ResourceLocation(structureName));
        }
        else
        {
            template = manager.getTemplate(new ResourceLocation(structureName));
            if (template == null)
            {
                throw TEMPLATE_INVALID_EXCEPTION.create(structureName);
            }
        }
        return template;
    }

    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder)
    {
        TemplateManager manager = MinecraftServer.INSTANCE.worlds[0].getStructureTemplateManager();
        return ISuggestionProvider.suggest(StructureCommand.listStructures(manager).stream().map(ResourceLocation::toString).collect(Collectors.toList()), builder);
    }
}
