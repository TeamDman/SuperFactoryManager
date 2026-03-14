package ca.teamdman.toml.toml_builder;

import ca.teamdman.antlr.IAstBuilder;
import ca.teamdman.langs.TomlParser;
import ca.teamdman.langs.TomlParserBaseVisitor;
import ca.teamdman.toml.ast.TomlAstNode;
import com.mojang.datafixers.util.Pair;
import org.antlr.v4.runtime.ParserRuleContext;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

public class TomlAstBuilder extends TomlParserBaseVisitor<TomlAstNode> implements IAstBuilder<TomlAstNode> {
    private final List<Pair<WeakReference<TomlAstNode>, ParserRuleContext>> contexts = new LinkedList<>();

    @Override
    public List<Pair<WeakReference<TomlAstNode>, ParserRuleContext>> contexts() {

        return contexts;
    }


    @Override
    public TomlAstNode visitDocument(TomlParser.DocumentContext ctx) {

        return super.visitDocument(ctx);
    }

}
