package com.example.mod;

import java.util.Arrays;
import java.util.Collection;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class LongArgumentType implements ArgumentType<Long>
{

    private static final Collection<String> EXAMPLES = Arrays.asList("0", "-1", "100");
    
    public static LongArgumentType longArg()
    {
        return new LongArgumentType();
    }
    
    public static long getLong(CommandContext<?> context, String name)
    {
        return context.getArgument(name, long.class);
    }
    
    @Override
    public <S> Long parse(StringReader reader) throws CommandSyntaxException
    {
        int start = reader.getCursor();
        while (reader.canRead() && StringReader.isAllowedNumber(reader.peek()))
            reader.skip();
        
        String number = reader.getString().substring(start, reader.getCursor());
        if (number.isEmpty())
        {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt().createWithContext(reader);
        }
        try
        {
            return Long.parseLong(number);
        }
        catch (NumberFormatException e)
        {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().createWithContext(reader, number);
        }
    }
    
    @Override
    public Collection<String> getExamples()
    {
        return EXAMPLES;
    }

}
