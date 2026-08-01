package real.inkognito338.murdermysteryutils.utils;

import java.util.*;
import java.util.regex.*;
import java.time.*;
import java.time.format.*;

/**
 * Project: MurderMysteryUtils
 * Author: inkognito338
 * Date: 30.06.2026
 */

@Deprecated
public final class MMUtilsPublicScriptAPI {

    private final List<Stmt> program;
    private final Map<String, FunctionDef> functions;

    private MMUtilsPublicScriptAPI(List<Stmt> program, Map<String, FunctionDef> functions) {
        this.program = program;
        this.functions = functions;
    }

    public static MMUtilsPublicScriptAPI compile(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        Parser parser = new Parser(tokens);
        parser.parseProgram();
        return new MMUtilsPublicScriptAPI(parser.program, parser.functions);
    }

    public Value run(ScriptContext ctx) {
        return run(ctx, 100_000);
    }

    public Value run(ScriptContext ctx, int maxSteps) {
        Interpreter interp = new Interpreter(ctx, maxSteps, functions);
        ExecResult r = interp.execBlock(program);
        return r != null && r.kind == ExecResult.Kind.RETURN ? r.value : Value.NULL;
    }

    // ========================================================================
    // ИСКЛЮЧЕНИЯ
    // ========================================================================

    public static class ScriptParseException extends RuntimeException {
        ScriptParseException(String msg) { super(msg); }
        ScriptParseException(String msg, int pos) { super(msg + " на позиции " + pos); }
    }

    public static class ScriptRuntimeException extends RuntimeException {
        ScriptRuntimeException(String msg) { super(msg); }
    }

    // ========================================================================
    // VALUE
    // ========================================================================

    public static final class Value {
        public enum Type {
            STRING, NUMBER, BOOL, NULL, MAP, LIST, FUNCTION, DATE, REGEX
        }

        public static final Value NULL = new Value(Type.NULL, null);
        public static final Value TRUE = new Value(Type.BOOL, Boolean.TRUE);
        public static final Value FALSE = new Value(Type.BOOL, Boolean.FALSE);

        public final Type type;
        private final Object raw;

        private Value(Type type, Object raw) {
            this.type = type;
            this.raw = raw;
        }

        public static Value of(String s) {
            return s == null ? NULL : new Value(Type.STRING, s);
        }

        public static Value of(boolean b) {
            return b ? TRUE : FALSE;
        }

        public static Value of(double n) {
            return new Value(Type.NUMBER, n);
        }

        public static Value of(int n) {
            return new Value(Type.NUMBER, (double) n);
        }

        public static Value ofMap(Map<String, Value> m) {
            if (m == null) return NULL;
            if (m.size() > 1000) throw new ScriptRuntimeException("Object too large (max 1000 properties)");
            return new Value(Type.MAP, Collections.unmodifiableMap(new HashMap<>(m)));
        }

        public static Value ofList(List<Value> l) {
            if (l == null) return NULL;
            if (l.size() > 10000) throw new ScriptRuntimeException("Array too large (max 10000 elements)");
            return new Value(Type.LIST, Collections.unmodifiableList(new ArrayList<>(l)));
        }

        public static Value ofFunction(FunctionDef f) {
            return new Value(Type.FUNCTION, f);
        }

        public static Value ofDate(LocalDateTime date) {
            return date == null ? NULL : new Value(Type.DATE, date);
        }

        public boolean isNull() { return type == Type.NULL; }

        public boolean truthy() {
            switch (type) {
                case BOOL: return (Boolean) raw;
                case NULL: return false;
                case STRING: return !((String) raw).isEmpty();
                case LIST: return !asListOrEmpty().isEmpty();
                case MAP: return !asMapOrEmpty().isEmpty();
                case NUMBER: return ((Number) raw).doubleValue() != 0;
                default: return true;
            }
        }

        public String asStringOrNull() {
            if (type == Type.STRING) return (String) raw;
            if (type == Type.NUMBER) return raw.toString();
            if (type == Type.BOOL) return raw.toString();
            if (type == Type.DATE) return raw.toString();
            if (type == Type.REGEX) return "/" + raw + "/";
            return null;
        }

        public double asNumberOrZero() {
            if (type == Type.NUMBER) return ((Number) raw).doubleValue();
            if (type == Type.STRING) {
                try { return Double.parseDouble((String) raw); }
                catch (NumberFormatException e) { return 0; }
            }
            return 0;
        }

        public int asIntOrZero() {
            return (int) asNumberOrZero();
        }

        @SuppressWarnings("unchecked")
        public Map<String, Value> asMapOrEmpty() {
            return type == Type.MAP ? (Map<String, Value>) raw : Collections.emptyMap();
        }

        @SuppressWarnings("unchecked")
        public List<Value> asListOrEmpty() {
            return type == Type.LIST ? (List<Value>) raw : Collections.emptyList();
        }

        public FunctionDef asFunctionOrNull() {
            return type == Type.FUNCTION ? (FunctionDef) raw : null;
        }

        @Override
        public String toString() {
            switch (type) {
                case STRING: return (String) raw;
                case NUMBER:
                case DATE:
                case BOOL:
                    return raw.toString();
                case NULL: return "null";
                case MAP: return "{...}";
                case LIST: return "[...]";
                case FUNCTION: return "[Function]";
                case REGEX: return "/" + raw + "/";
                default: return type.toString();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Value)) return false;
            Value other = (Value) o;
            if (this.type != other.type) return false;
            if (type == Type.NULL) return true;
            if (type == Type.NUMBER) {
                return Double.compare(asNumberOrZero(), other.asNumberOrZero()) == 0;
            }
            if (type == Type.REGEX) {
                return raw.toString().equals(other.raw.toString());
            }
            return Objects.equals(raw, other.raw);
        }

        @Override
        public int hashCode() {
            if (type == Type.NUMBER) return Double.hashCode(asNumberOrZero());
            return raw == null ? 0 : raw.hashCode();
        }
    }

    // ========================================================================
    // SCRIPT CONTEXT
    // ========================================================================

    public static final class ScriptContext {
        private final Map<String, Value> globals = new HashMap<>();
        private final List<TimerTask> timers = new ArrayList<>();
        private boolean stopped = false;

        public void set(String name, String value) {
            globals.put(name, Value.of(value));
        }

        public void set(String name, boolean value) {
            globals.put(name, Value.of(value));
        }

        public void set(String name, double value) {
            globals.put(name, Value.of(value));
        }

        public void set(String name, Value value) {
            globals.put(name, value == null ? Value.NULL : value);
        }

        public void registerTimer(Runnable task, long delayMs, boolean repeat) {
            if (stopped) return;
            TimerTask timer = new TimerTask(task, delayMs, repeat);
            timers.add(timer);
            timer.start();
        }

        public void stopAllTimers() {
            stopped = true;
            for (TimerTask timer : timers) {
                timer.stop();
            }
            timers.clear();
        }

        Value getGlobal(String name) {
            Value v = globals.get(name);
            return v == null ? Value.NULL : v;
        }
    }

    private static class TimerTask {
        private final Runnable task;
        private final long delayMs;
        private final boolean repeat;
        private Thread thread;
        private volatile boolean running = true;

        TimerTask(Runnable task, long delayMs, boolean repeat) {
            this.task = task;
            this.delayMs = Math.min(delayMs, 60000);
            this.repeat = repeat;
        }

        void start() {
            thread = new Thread(() -> {
                do {
                    try {
                        Thread.sleep(delayMs);
                        if (running) {
                            task.run();
                        }
                    } catch (InterruptedException ignored) {
                        break;
                    }
                } while (running && repeat);
            });
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running = false;
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    // ========================================================================
    // ФУНКЦИИ
    // ========================================================================

    public static final class FunctionDef {
        public final String name;
        public final List<String> params;
        public final List<Stmt> body;

        public FunctionDef(String name, List<String> params, List<Stmt> body) {
            this.name = name;
            this.params = params;
            this.body = body;
        }
    }

    // ========================================================================
    // AST НОДЫ
    // ========================================================================

    private interface Node {}
    private interface Expr extends Node {}
    private interface Stmt extends Node {}

    private static final class IfStmt implements Stmt {
        final Expr cond; final List<Stmt> thenB; final List<Stmt> elseB;
        IfStmt(Expr c, List<Stmt> t, List<Stmt> e) { cond = c; thenB = t; elseB = e; }
    }

    private static final class WhileStmt implements Stmt {
        final Expr cond; final List<Stmt> body;
        WhileStmt(Expr c, List<Stmt> b) { cond = c; body = b; }
    }

    private static final class DoWhileStmt implements Stmt {
        final Expr cond; final List<Stmt> body;
        DoWhileStmt(Expr c, List<Stmt> b) { cond = c; body = b; }
    }

    private static final class ForStmt implements Stmt {
        final String varName; final Expr init; final Expr cond; final Expr update; final List<Stmt> body;
        ForStmt(String v, Expr i, Expr c, Expr u, List<Stmt> b) {
            varName = v; init = i; cond = c; update = u; body = b;
        }
    }

    private static final class ForOfStmt implements Stmt {
        final String varName; final Expr iterExpr; final List<Stmt> body;
        ForOfStmt(String v, Expr it, List<Stmt> b) { varName = v; iterExpr = it; body = b; }
    }

    private static final class ForInStmt implements Stmt {
        final String varName; final Expr objExpr; final List<Stmt> body;
        ForInStmt(String v, Expr o, List<Stmt> b) { varName = v; objExpr = o; body = b; }
    }

    private static final class SwitchStmt implements Stmt {
        final Expr value; final List<CaseClause> cases; final List<Stmt> defaultCase;
        SwitchStmt(Expr v, List<CaseClause> c, List<Stmt> d) { value = v; cases = c; defaultCase = d; }
    }

    private static final class CaseClause {
        final Expr value; final List<Stmt> body;
        CaseClause(Expr v, List<Stmt> b) { value = v; body = b; }
    }

    private static final class TryCatchStmt implements Stmt {
        final List<Stmt> tryBody; final String catchVar; final List<Stmt> catchBody;
        TryCatchStmt(List<Stmt> t, String c, List<Stmt> cb) { tryBody = t; catchVar = c; catchBody = cb; }
    }

    static final class ReturnStmt implements Stmt {
        final Expr value; ReturnStmt(Expr v) { value = v; }
    }

    private static final class VarStmt implements Stmt {
        final String name; final Expr value;
        VarStmt(String n, Expr v) { name = n; value = v; }
    }

    private static final class FuncDefStmt implements Stmt {
        final FunctionDef func;
        FuncDefStmt(FunctionDef f) { func = f; }
    }

    private static final class CallStmt implements Stmt {
        final String name; final List<Expr> args;
        CallStmt(String n, List<Expr> a) { name = n; args = a; }
    }

    private static final class BreakStmt implements Stmt {}
    private static final class ContinueStmt implements Stmt {}

    static final class StringLit implements Expr { final String v; StringLit(String v) { this.v = v; } }
    private static final class NumberLit implements Expr { final double v; NumberLit(double v) { this.v = v; } }
    private static final class BoolLit implements Expr { final boolean v; BoolLit(boolean v) { this.v = v; } }
    private static final class NullLit implements Expr {}
    private static final class IdentRef implements Expr { final String name; IdentRef(String n) { name = n; } }
    private static final class FieldAccess implements Expr { final Expr base; final String name; FieldAccess(Expr b, String n) { base = b; name = n; } }
    private static final class OptionalChain implements Expr { final Expr base; final String field; OptionalChain(Expr b, String f) { base = b; field = f; } }
    private static final class IndexExpr implements Expr { final Expr base; final Expr index; IndexExpr(Expr b, Expr i) { base = b; index = i; } }
    private static final class ArrayLit implements Expr { final List<Expr> items; ArrayLit(List<Expr> i) { items = i; } }
    private static final class ObjectLit implements Expr { final List<String> keys; final List<Expr> values; ObjectLit(List<String> k, List<Expr> v) { keys = k; values = v; } }
    private static final class Ternary implements Expr { final Expr cond, thenE, elseE; Ternary(Expr c, Expr t, Expr e) { cond = c; thenE = t; elseE = e; } }
    private static final class MethodCall implements Expr { final Expr target; final String method; final List<Expr> args; MethodCall(Expr t, String m, List<Expr> a) { target = t; method = m; args = a; } }
    private static final class FuncCall implements Expr { final String name; final List<Expr> args; FuncCall(String n, List<Expr> a) { name = n; args = a; } }
    private static final class FunctionLit implements Expr { final FunctionDef func; FunctionLit(FunctionDef f) { func = f; } }
    private static final class SpreadExpr implements Expr { final Expr target; SpreadExpr(Expr t) { target = t; } }
    private static final class NullishCoalesce implements Expr { final Expr l, r; NullishCoalesce(Expr l, Expr r) { this.l = l; this.r = r; } }
    private static final class DestructArray implements Expr { final List<String> vars; final Expr source; DestructArray(List<String> v, Expr s) { vars = v; source = s; } }
    private static final class DestructObject implements Expr { final Map<String, String> vars; final Expr source; DestructObject(Map<String, String> v, Expr s) { vars = v; source = s; } }
    private static final class TemplateString implements Expr { final String value; TemplateString(String v) { value = v; } }

    private static final class UnaryOp implements Expr { final String op; final Expr expr; UnaryOp(String o, Expr e) { op = o; expr = e; } }
    private static final class BinaryOp implements Expr { final String op; final Expr l, r; BinaryOp(String o, Expr l, Expr r) { this.op = o; this.l = l; this.r = r; } }
    private static final class InOp implements Expr { final Expr item, list; InOp(Expr item, Expr list) { this.item = item; this.list = list; } }
    private static final class InstanceOfOp implements Expr { final Expr obj; final String type; InstanceOfOp(Expr o, String t) { obj = o; type = t; } }
    private static final class TypeOfOp implements Expr { final Expr expr; TypeOfOp(Expr e) { expr = e; } }

    private enum CmpOp { EQ, NEQ, STRICT_EQ, STRICT_NEQ, GT, LT, GTE, LTE, CONTAINS, MATCHES, STARTS_WITH, ENDS_WITH }
    private static final class Cmp implements Expr { final Expr l; final CmpOp op; final Expr r; Cmp(Expr l, CmpOp op, Expr r) { this.l = l; this.op = op; this.r = r; } }
    private static final class And implements Expr { final Expr l, r; And(Expr l, Expr r) { this.l = l; this.r = r; } }
    private static final class Or implements Expr { final Expr l, r; Or(Expr l, Expr r) { this.l = l; this.r = r; } }
    private static final class Not implements Expr { final Expr e; Not(Expr e) { this.e = e; } }

    // ========================================================================
    // ЛЕКСЕР
    // ========================================================================

    private enum TokType {
        IF, ELSE, SWITCH, CASE, DEFAULT, WHILE, DO, FOR, OF, IN, BREAK, CONTINUE,
        RETURN, LET, VAR, FUNCTION, TRY, CATCH, TYPEOF, INSTANCEOF,
        AND, OR, NOT,
        EQ, NEQ, STRICT_EQ, STRICT_NEQ, ASSIGN, PLUS, MINUS, MULTIPLY, DIVIDE, MOD, POWER,
        PLUS_EQ, MINUS_EQ, MULTIPLY_EQ, DIVIDE_EQ, MOD_EQ, POWER_EQ,
        INCREMENT, DECREMENT,
        GT, LT, GTE, LTE,
        QUESTION, COLON, DOUBLE_QUESTION,
        TRUE, FALSE, NULL,
        IDENT, STRING, NUMBER, TEMPLATE,
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        SEMI, DOT, COMMA, SPREAD, ARROW,
        EOF
    }

    private static final class Token {
        final TokType type;
        final String text;
        final int pos;
        Token(TokType type, String text, int pos) { this.type = type; this.text = text; this.pos = pos; }
        @Override public String toString() { return type + "('" + text + "')@" + pos; }
    }

    private static final Map<String, TokType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("if", TokType.IF);
        KEYWORDS.put("else", TokType.ELSE);
        KEYWORDS.put("switch", TokType.SWITCH);
        KEYWORDS.put("case", TokType.CASE);
        KEYWORDS.put("default", TokType.DEFAULT);
        KEYWORDS.put("while", TokType.WHILE);
        KEYWORDS.put("do", TokType.DO);
        KEYWORDS.put("for", TokType.FOR);
        KEYWORDS.put("of", TokType.OF);
        KEYWORDS.put("in", TokType.IN);
        KEYWORDS.put("break", TokType.BREAK);
        KEYWORDS.put("continue", TokType.CONTINUE);
        KEYWORDS.put("return", TokType.RETURN);
        KEYWORDS.put("let", TokType.LET);
        KEYWORDS.put("var", TokType.VAR);
        KEYWORDS.put("function", TokType.FUNCTION);
        KEYWORDS.put("try", TokType.TRY);
        KEYWORDS.put("catch", TokType.CATCH);
        KEYWORDS.put("typeof", TokType.TYPEOF);
        KEYWORDS.put("instanceof", TokType.INSTANCEOF);
        KEYWORDS.put("true", TokType.TRUE);
        KEYWORDS.put("false", TokType.FALSE);
        KEYWORDS.put("null", TokType.NULL);
    }

    private static final Map<String, TokType> COMPOUND_ASSIGN = new HashMap<>();
    static {
        COMPOUND_ASSIGN.put("+=", TokType.PLUS_EQ);
        COMPOUND_ASSIGN.put("-=", TokType.MINUS_EQ);
        COMPOUND_ASSIGN.put("*=", TokType.MULTIPLY_EQ);
        COMPOUND_ASSIGN.put("/=", TokType.DIVIDE_EQ);
        COMPOUND_ASSIGN.put("%=", TokType.MOD_EQ);
        COMPOUND_ASSIGN.put("**=", TokType.POWER_EQ);
    }

    private static final class Lexer {
        private final String src;
        private int i = 0;

        Lexer(String src) { this.src = src; }

        List<Token> tokenize() {
            List<Token> out = new ArrayList<>();
            while (true) {
                skipWs();
                if (i >= src.length()) { out.add(new Token(TokType.EOF, "", i)); break; }
                char c = src.charAt(i);
                int start = i;

                if (Character.isLetter(c) || c == '_' || c == '$') {
                    String w = readIdent();
                    TokType kw = KEYWORDS.get(w);
                    out.add(new Token(kw != null ? kw : TokType.IDENT, w, start));
                    continue;
                }

                if (Character.isDigit(c)) {
                    out.add(readNumber(start));
                    continue;
                }

                if (c == '"' || c == '\'') {
                    out.add(readString(c, start));
                    continue;
                }

                if (c == '`') {
                    out.add(readTemplate(start));
                    continue;
                }

                if (c == '/') {
                    if (peek() == '/') {
                        while (i < src.length() && src.charAt(i) != '\n') i++;
                        continue;
                    }
                    if (peek() == '*') {
                        i += 2;
                        while (i < src.length() && !(src.charAt(i) == '*' && peek() == '/')) {
                            i++;
                        }
                        if (i < src.length()) i += 2;
                        continue;
                    }
                }

                String two = c + "" + peek();
                TokType compound = COMPOUND_ASSIGN.get(two);
                if (compound != null) {
                    out.add(new Token(compound, two, start));
                    i += 2;
                    continue;
                }

                switch (two) {
                    case "==": out.add(new Token(TokType.EQ, "==", start)); i += 2; continue;
                    case "!=": out.add(new Token(TokType.NEQ, "!=", start)); i += 2; continue;
                    case "&&": out.add(new Token(TokType.AND, "&&", start)); i += 2; continue;
                    case "||": out.add(new Token(TokType.OR, "||", start)); i += 2; continue;
                    case "++": out.add(new Token(TokType.INCREMENT, "++", start)); i += 2; continue;
                    case "--": out.add(new Token(TokType.DECREMENT, "--", start)); i += 2; continue;
                    case "**": out.add(new Token(TokType.POWER, "**", start)); i += 2; continue;
                    case ">=": out.add(new Token(TokType.GTE, ">=", start)); i += 2; continue;
                    case "<=": out.add(new Token(TokType.LTE, "<=", start)); i += 2; continue;
                    case "??": out.add(new Token(TokType.DOUBLE_QUESTION, "??", start)); i += 2; continue;
                    case "=>": out.add(new Token(TokType.ARROW, "=>", start)); i += 2; continue;
                }

                if (c == '=' && peek() == '=' && peek(2) == '=') {
                    out.add(new Token(TokType.STRICT_EQ, "===", start)); i += 3; continue;
                }
                if (c == '!' && peek() == '=' && peek(2) == '=') {
                    out.add(new Token(TokType.STRICT_NEQ, "!==", start)); i += 3; continue;
                }
                if (c == '.' && peek() == '.' && peek(2) == '.') {
                    out.add(new Token(TokType.SPREAD, "...", start)); i += 3; continue;
                }

                switch (c) {
                    case '(': out.add(new Token(TokType.LPAREN, "(", start)); i++; break;
                    case ')': out.add(new Token(TokType.RPAREN, ")", start)); i++; break;
                    case '{': out.add(new Token(TokType.LBRACE, "{", start)); i++; break;
                    case '}': out.add(new Token(TokType.RBRACE, "}", start)); i++; break;
                    case '[': out.add(new Token(TokType.LBRACKET, "[", start)); i++; break;
                    case ']': out.add(new Token(TokType.RBRACKET, "]", start)); i++; break;
                    case ';': out.add(new Token(TokType.SEMI, ";", start)); i++; break;
                    case ',': out.add(new Token(TokType.COMMA, ",", start)); i++; break;
                    case '?': out.add(new Token(TokType.QUESTION, "?", start)); i++; break;
                    case ':': out.add(new Token(TokType.COLON, ":", start)); i++; break;
                    case '+': out.add(new Token(TokType.PLUS, "+", start)); i++; break;
                    case '-': out.add(new Token(TokType.MINUS, "-", start)); i++; break;
                    case '*': out.add(new Token(TokType.MULTIPLY, "*", start)); i++; break;
                    case '/': out.add(new Token(TokType.DIVIDE, "/", start)); i++; break;
                    case '%': out.add(new Token(TokType.MOD, "%", start)); i++; break;
                    case '=': out.add(new Token(TokType.ASSIGN, "=", start)); i++; break;
                    case '>': out.add(new Token(TokType.GT, ">", start)); i++; break;
                    case '<': out.add(new Token(TokType.LT, "<", start)); i++; break;
                    case '!': out.add(new Token(TokType.NOT, "!", start)); i++; break;
                    case '.': out.add(new Token(TokType.DOT, ".", start)); i++; break;
                    default:
                        throw new ScriptParseException("Неожиданный символ '" + c + "'", start);
                }
            }
            return out;
        }

        private Token readNumber(int start) {
            StringBuilder sb = new StringBuilder();
            boolean hasDot = false;
            while (i < src.length()) {
                char c = src.charAt(i);
                if (Character.isDigit(c)) { sb.append(c); i++; }
                else if (c == '.' && !hasDot) { hasDot = true; sb.append(c); i++; }
                else break;
            }
            try {
                Double.parseDouble(sb.toString());
                return new Token(TokType.NUMBER, sb.toString(), start);
            } catch (NumberFormatException e) {
                throw new ScriptParseException("Некорректное число: " + sb, start);
            }
        }

        private Token readString(char quote, int start) {
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < src.length() && src.charAt(i) != quote) {
                char cur = src.charAt(i);
                if (cur == '\\' && i + 1 < src.length()) {
                    char next = src.charAt(i + 1);
                    switch (next) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case '\\': sb.append('\\'); break;
                        default: sb.append(next); break;
                    }
                    i += 2;
                } else {
                    sb.append(cur);
                    i++;
                }
            }
            if (i >= src.length()) throw new ScriptParseException("Незакрытая строка", start);
            i++;
            return new Token(TokType.STRING, sb.toString(), start);
        }

        private Token readTemplate(int start) {
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < src.length()) {
                char c = src.charAt(i);
                if (c == '`') {
                    i++;
                    return new Token(TokType.TEMPLATE, sb.toString(), start);
                }
                if (c == '$' && peek() == '{') {
                    sb.append("${");
                    i += 2;
                    while (i < src.length() && src.charAt(i) != '}') {
                        sb.append(src.charAt(i));
                        i++;
                    }
                    if (i < src.length()) {
                        sb.append('}');
                        i++;
                    }
                } else if (c == '\\') {
                    i++;
                    if (i < src.length()) {
                        char next = src.charAt(i);
                        switch (next) {
                            case '`': sb.append('`'); break;
                            case '\\': sb.append('\\'); break;
                            case '$': sb.append('$'); break;
                            default: sb.append(next); break;
                        }
                        i++;
                    }
                } else {
                    sb.append(c);
                    i++;
                }
            }
            throw new ScriptParseException("Незакрытая шаблонная строка", start);
        }

        private String readIdent() {
            StringBuilder sb = new StringBuilder();
            while (i < src.length() && (Character.isLetterOrDigit(src.charAt(i)) ||
                    src.charAt(i) == '_' || src.charAt(i) == '$')) {
                sb.append(src.charAt(i));
                i++;
            }
            return sb.toString();
        }

        private char peek() {
            return i < src.length() ? src.charAt(i) : '\0';
        }

        private char peek(int off) {
            int p = i + off;
            return p < src.length() ? src.charAt(p) : '\0';
        }

        private void skipWs() {
            while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
                i++;
            }
        }
    }

    // ========================================================================
    // ПАРСЕР
    // ========================================================================

    private static final class Parser {
        private final List<Token> tokens;
        private int pos = 0;
        private final List<Stmt> program = new ArrayList<>();
        private final Map<String, FunctionDef> functions = new HashMap<>();
        private int depth = 0;
        private static final int MAX_DEPTH = 200;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        void parseProgram() {
            while (!check(TokType.EOF)) {
                Stmt stmt = parseStmt();
                if (stmt != null) program.add(stmt);
            }
        }

        private Stmt parseStmt() {
            depth++;
            if (depth > MAX_DEPTH) {
                throw new ScriptParseException("Превышена максимальная глубина вложенности", peek().pos);
            }
            try {
                if (match(TokType.FUNCTION)) return parseFunctionDef();
                if (match(TokType.IF)) return parseIf();
                if (match(TokType.SWITCH)) return parseSwitch();
                if (match(TokType.WHILE)) return parseWhile();
                if (match(TokType.DO)) return parseDoWhile();
                if (match(TokType.FOR)) return parseFor();
                if (match(TokType.RETURN)) {
                    Expr v = check(TokType.SEMI) ? null : parseExpr();
                    match(TokType.SEMI);
                    return new ReturnStmt(v);
                }
                if (match(TokType.LET) || match(TokType.VAR)) {
                    return parseVarDecl();
                }
                if (match(TokType.BREAK)) { match(TokType.SEMI); return new BreakStmt(); }
                if (match(TokType.CONTINUE)) { match(TokType.SEMI); return new ContinueStmt(); }
                if (match(TokType.TRY)) return parseTryCatch();

                if (check(TokType.IDENT)) {
                    Token id = advance();
                    if (match(TokType.LPAREN)) {
                        List<Expr> args = parseArgs();
                        expect(TokType.RPAREN, "Ожидалась ')'");
                        match(TokType.SEMI);
                        return new CallStmt(id.text, args);
                    }
                    if (match(TokType.ASSIGN)) {
                        Expr value = parseExpr();
                        match(TokType.SEMI);
                        return new VarStmt(id.text, value);
                    }
                }

                if (check(TokType.LBRACE)) {
                    return parseDestructStmt();
                }
                if (check(TokType.LBRACKET)) {
                    return parseDestructArrayStmt();
                }

                throw new ScriptParseException("Неожиданный токен: " + peek(), peek().pos);
            } finally {
                depth--;
            }
        }

        private Stmt parseVarDecl() {
            Token name = expect(TokType.IDENT, "Ожидалось имя переменной");
            if (match(TokType.ASSIGN)) {
                Expr v = parseExpr();
                match(TokType.SEMI);
                return new VarStmt(name.text, v);
            }
            match(TokType.SEMI);
            return new VarStmt(name.text, null);
        }

        private Stmt parseDestructStmt() {
            expect(TokType.LBRACE, "Ожидалась '{'");
            Map<String, String> vars = new HashMap<>();
            while (!check(TokType.RBRACE)) {
                Token key = expect(TokType.IDENT, "Ожидался ключ");
                String varName = key.text;
                if (match(TokType.COLON)) {
                    varName = expect(TokType.IDENT, "Ожидалось имя переменной").text;
                }
                vars.put(key.text, varName);
                if (!match(TokType.COMMA)) break;
            }
            expect(TokType.RBRACE, "Ожидалась '}'");
            expect(TokType.ASSIGN, "Ожидался '='");
            Expr source = parseExpr();
            match(TokType.SEMI);
            return new VarStmt("", new DestructObject(vars, source));
        }

        private Stmt parseDestructArrayStmt() {
            expect(TokType.LBRACKET, "Ожидалась '['");
            List<String> vars = new ArrayList<>();
            while (!check(TokType.RBRACKET)) {
                vars.add(expect(TokType.IDENT, "Ожидалось имя переменной").text);
                if (!match(TokType.COMMA)) break;
            }
            expect(TokType.RBRACKET, "Ожидалась ']'");
            expect(TokType.ASSIGN, "Ожидался '='");
            Expr source = parseExpr();
            match(TokType.SEMI);
            return new VarStmt("", new DestructArray(vars, source));
        }

        private Stmt parseFunctionDef() {
            Token name = expect(TokType.IDENT, "Ожидалось имя функции");
            expect(TokType.LPAREN, "Ожидалась '('");

            List<String> params = new ArrayList<>();
            if (!check(TokType.RPAREN)) {
                do {
                    params.add(expect(TokType.IDENT, "Ожидалось имя параметра").text);
                } while (match(TokType.COMMA));
            }
            expect(TokType.RPAREN, "Ожидалась ')'");

            if (match(TokType.ARROW)) {
                if (check(TokType.LBRACE)) {
                    expect(TokType.LBRACE, "Ожидалась '{'");
                    List<Stmt> body = new ArrayList<>();
                    while (!check(TokType.RBRACE)) body.add(parseStmt());
                    expect(TokType.RBRACE, "Ожидалась '}'");
                    FunctionDef func = new FunctionDef(name.text, params, body);
                    functions.put(name.text, func);
                    return new FuncDefStmt(func);
                } else {
                    Expr expr = parseExpr();
                    List<Stmt> body = new ArrayList<>();
                    body.add(new ReturnStmt(expr));
                    FunctionDef func = new FunctionDef(name.text, params, body);
                    functions.put(name.text, func);
                    return new FuncDefStmt(func);
                }
            }

            expect(TokType.LBRACE, "Ожидалась '{'");
            List<Stmt> body = new ArrayList<>();
            while (!check(TokType.RBRACE)) body.add(parseStmt());
            expect(TokType.RBRACE, "Ожидалась '}'");

            FunctionDef func = new FunctionDef(name.text, params, body);
            functions.put(name.text, func);
            return new FuncDefStmt(func);
        }

        private Stmt parseIf() {
            expect(TokType.LPAREN, "Ожидалась '(' после if");
            Expr cond = parseExpr();
            expect(TokType.RPAREN, "Ожидалась ')'");
            expect(TokType.LBRACE, "Ожидалась '{'");

            List<Stmt> thenB = new ArrayList<>();
            while (!check(TokType.RBRACE)) thenB.add(parseStmt());
            expect(TokType.RBRACE, "Ожидалась '}'");

            List<Stmt> elseB = null;
            if (match(TokType.ELSE)) {
                if (check(TokType.IF)) {
                    elseB = new ArrayList<>();
                    elseB.add(parseIf());
                } else {
                    expect(TokType.LBRACE, "Ожидалась '{' после else");
                    elseB = new ArrayList<>();
                    while (!check(TokType.RBRACE)) elseB.add(parseStmt());
                    expect(TokType.RBRACE, "Ожидалась '}'");
                }
            }
            return new IfStmt(cond, thenB, elseB);
        }

        private Stmt parseSwitch() {
            expect(TokType.LPAREN, "Ожидалась '(' после switch");
            Expr value = parseExpr();
            expect(TokType.RPAREN, "Ожидалась ')'");
            expect(TokType.LBRACE, "Ожидалась '{'");

            List<CaseClause> cases = new ArrayList<>();
            List<Stmt> defaultCase = null;

            while (!check(TokType.RBRACE)) {
                if (match(TokType.CASE)) {
                    Expr caseVal = parseExpr();
                    expect(TokType.COLON, "Ожидался ':' после case");
                    List<Stmt> body = new ArrayList<>();
                    while (!check(TokType.RBRACE) && !check(TokType.CASE) && !check(TokType.DEFAULT)) {
                        body.add(parseStmt());
                    }
                    cases.add(new CaseClause(caseVal, body));
                } else if (match(TokType.DEFAULT)) {
                    expect(TokType.COLON, "Ожидался ':' после default");
                    defaultCase = new ArrayList<>();
                    while (!check(TokType.RBRACE) && !check(TokType.CASE) && !check(TokType.DEFAULT)) {
                        defaultCase.add(parseStmt());
                    }
                } else {
                    throw new ScriptParseException("Ожидался case или default", peek().pos);
                }
            }
            expect(TokType.RBRACE, "Ожидалась '}'");
            return new SwitchStmt(value, cases, defaultCase);
        }

        private Stmt parseWhile() {
            expect(TokType.LPAREN, "Ожидалась '(' после while");
            Expr cond = parseExpr();
            expect(TokType.RPAREN, "Ожидалась ')'");
            expect(TokType.LBRACE, "Ожидалась '{'");
            List<Stmt> body = new ArrayList<>();
            while (!check(TokType.RBRACE)) body.add(parseStmt());
            expect(TokType.RBRACE, "Ожидалась '}'");
            return new WhileStmt(cond, body);
        }

        private Stmt parseDoWhile() {
            expect(TokType.LBRACE, "Ожидалась '{' после do");
            List<Stmt> body = new ArrayList<>();
            while (!check(TokType.RBRACE)) body.add(parseStmt());
            expect(TokType.RBRACE, "Ожидалась '}'");
            expect(TokType.WHILE, "Ожидался 'while' после do");
            expect(TokType.LPAREN, "Ожидалась '('");
            Expr cond = parseExpr();
            expect(TokType.RPAREN, "Ожидалась ')'");
            match(TokType.SEMI);
            return new DoWhileStmt(cond, body);
        }

        private Stmt parseFor() {
            expect(TokType.LPAREN, "Ожидалась '(' после for");

            String varName = null;
            Expr init = null;
            Expr cond = null;
            Expr update = null;

            if (match(TokType.LET) || match(TokType.VAR)) {
                Token name = expect(TokType.IDENT, "Ожидалось имя переменной");
                varName = name.text;
                expect(TokType.ASSIGN, "Ожидался '='");
                init = parseExpr();
                match(TokType.SEMI);
            } else if (!check(TokType.SEMI)) {
                init = parseExpr();
                match(TokType.SEMI);
            } else {
                match(TokType.SEMI);
            }

            if (!check(TokType.SEMI)) {
                cond = parseExpr();
            }
            match(TokType.SEMI);

            if (!check(TokType.RPAREN)) {
                update = parseExpr();
            }
            expect(TokType.RPAREN, "Ожидалась ')'");
            expect(TokType.LBRACE, "Ожидалась '{'");

            List<Stmt> body = new ArrayList<>();
            while (!check(TokType.RBRACE)) body.add(parseStmt());
            expect(TokType.RBRACE, "Ожидалась '}'");

            if (varName != null && init != null) {
                return new ForStmt(varName, init, cond, update, body);
            }
            return new ForStmt(null, init, cond, update, body);
        }

        private Stmt parseTryCatch() {
            expect(TokType.LBRACE, "Ожидалась '{' после try");
            List<Stmt> tryBody = new ArrayList<>();
            while (!check(TokType.RBRACE)) tryBody.add(parseStmt());
            expect(TokType.RBRACE, "Ожидалась '}'");

            expect(TokType.CATCH, "Ожидался catch");
            expect(TokType.LPAREN, "Ожидалась '('");
            Token catchVar = expect(TokType.IDENT, "Ожидалось имя переменной ошибки");
            expect(TokType.RPAREN, "Ожидалась ')'");
            expect(TokType.LBRACE, "Ожидалась '{'");
            List<Stmt> catchBody = new ArrayList<>();
            while (!check(TokType.RBRACE)) catchBody.add(parseStmt());
            expect(TokType.RBRACE, "Ожидалась '}'");

            return new TryCatchStmt(tryBody, catchVar.text, catchBody);
        }

        private Expr parseExpr() { return parseTernary(); }

        private Expr parseTernary() {
            Expr cond = parseNullish();
            if (match(TokType.QUESTION)) {
                Expr thenE = parseExpr();
                expect(TokType.COLON, "Ожидался ':'");
                Expr elseE = parseExpr();
                return new Ternary(cond, thenE, elseE);
            }
            return cond;
        }

        private Expr parseNullish() {
            Expr l = parseOr();
            if (match(TokType.DOUBLE_QUESTION)) {
                return new NullishCoalesce(l, parseOr());
            }
            return l;
        }

        private Expr parseOr() {
            Expr l = parseAnd();
            while (match(TokType.OR)) l = new Or(l, parseAnd());
            return l;
        }

        private Expr parseAnd() {
            Expr l = parseNot();
            while (match(TokType.AND)) l = new And(l, parseNot());
            return l;
        }

        private Expr parseNot() {
            if (match(TokType.NOT)) return new Not(parseNot());
            return parseComparison();
        }

        private Expr parseComparison() {
            Expr l = parseConcat();

            if (match(TokType.EQ)) return new Cmp(l, CmpOp.EQ, parseConcat());
            if (match(TokType.NEQ)) return new Cmp(l, CmpOp.NEQ, parseConcat());
            if (match(TokType.STRICT_EQ)) return new Cmp(l, CmpOp.STRICT_EQ, parseConcat());
            if (match(TokType.STRICT_NEQ)) return new Cmp(l, CmpOp.STRICT_NEQ, parseConcat());
            if (match(TokType.GT)) return new Cmp(l, CmpOp.GT, parseConcat());
            if (match(TokType.LT)) return new Cmp(l, CmpOp.LT, parseConcat());
            if (match(TokType.GTE)) return new Cmp(l, CmpOp.GTE, parseConcat());
            if (match(TokType.LTE)) return new Cmp(l, CmpOp.LTE, parseConcat());

            if (match(TokType.IN)) {
                Expr list = parseConcat();
                return new InOp(l, list);
            }

            if (match(TokType.INSTANCEOF)) {
                String type = expect(TokType.IDENT, "Ожидалось имя типа").text;
                return new InstanceOfOp(l, type);
            }

            if (match(TokType.TYPEOF)) {
                return new TypeOfOp(l);
            }

            return l;
        }

        private Expr parseConcat() {
            Expr l = parseAdditive();
            while (match(TokType.PLUS)) {
                Expr r = parseAdditive();
                l = new BinaryOp("+", l, r);
            }
            return l;
        }

        private Expr parseAdditive() {
            Expr l = parseMultiplicative();
            while (true) {
                if (match(TokType.PLUS)) l = new BinaryOp("+", l, parseMultiplicative());
                else if (match(TokType.MINUS)) l = new BinaryOp("-", l, parseMultiplicative());
                else break;
            }
            return l;
        }

        private Expr parseMultiplicative() {
            Expr l = parseUnary();
            while (true) {
                if (match(TokType.MULTIPLY)) l = new BinaryOp("*", l, parseUnary());
                else if (match(TokType.DIVIDE)) l = new BinaryOp("/", l, parseUnary());
                else if (match(TokType.MOD)) l = new BinaryOp("%", l, parseUnary());
                else if (match(TokType.POWER)) l = new BinaryOp("**", l, parseUnary());
                else break;
            }
            return l;
        }

        private Expr parseUnary() {
            if (match(TokType.INCREMENT)) {
                Token id = expect(TokType.IDENT, "Ожидалось имя переменной");
                return new UnaryOp("++", new IdentRef(id.text));
            }
            if (match(TokType.DECREMENT)) {
                Token id = expect(TokType.IDENT, "Ожидалось имя переменной");
                return new UnaryOp("--", new IdentRef(id.text));
            }
            if (match(TokType.PLUS)) return parseUnary();
            if (match(TokType.MINUS)) return new UnaryOp("-", parseUnary());
            return parsePostfix();
        }

        private Expr parsePostfix() {
            Expr base = parsePrimary();
            while (true) {
                if (match(TokType.LBRACKET)) {
                    Expr idx = parseExpr();
                    expect(TokType.RBRACKET, "Ожидалась ']'");
                    base = new IndexExpr(base, idx);
                    continue;
                }
                if (match(TokType.DOT)) {
                    Token name = expect(TokType.IDENT, "Ожидалось имя поля после '.'");
                    if (check(TokType.LPAREN)) {
                        advance();
                        List<Expr> args = parseArgs();
                        expect(TokType.RPAREN, "Ожидалась ')'");
                        base = new MethodCall(base, name.text, args);
                        continue;
                    }
                    base = new FieldAccess(base, name.text);
                    continue;
                }
                if (match(TokType.QUESTION)) {
                    expect(TokType.DOT, "Ожидался '.' после '?'");
                    Token name = expect(TokType.IDENT, "Ожидалось имя поля");
                    base = new OptionalChain(base, name.text);
                    continue;
                }
                if (match(TokType.LPAREN)) {
                    if (!(base instanceof IdentRef)) {
                        throw new ScriptParseException("Можно вызывать только функции по имени", peek().pos);
                    }
                    List<Expr> args = parseArgs();
                    expect(TokType.RPAREN, "Ожидалась ')'");
                    base = new FuncCall(((IdentRef) base).name, args);
                    continue;
                }
                if (match(TokType.INCREMENT)) {
                    if (!(base instanceof IdentRef)) {
                        throw new ScriptParseException("Можно инкрементировать только переменные", peek().pos);
                    }
                    base = new UnaryOp("post++", base);
                    continue;
                }
                if (match(TokType.DECREMENT)) {
                    if (!(base instanceof IdentRef)) {
                        throw new ScriptParseException("Можно декрементировать только переменные", peek().pos);
                    }
                    base = new UnaryOp("post--", base);
                    continue;
                }
                break;
            }
            return base;
        }

        private Expr parsePrimary() {
            depth++;
            try {
                if (match(TokType.LPAREN)) {
                    Expr inner = parseExpr();
                    expect(TokType.RPAREN, "Ожидалась ')'");
                    return inner;
                }
                if (match(TokType.STRING)) return new StringLit(previous().text);
                if (match(TokType.NUMBER)) return new NumberLit(Double.parseDouble(previous().text));
                if (match(TokType.TRUE)) return new BoolLit(true);
                if (match(TokType.FALSE)) return new BoolLit(false);
                if (match(TokType.NULL)) return new NullLit();

                if (match(TokType.SPREAD)) {
                    Expr target = parsePrimary();
                    return new SpreadExpr(target);
                }

                if (match(TokType.LBRACKET)) {
                    if (match(TokType.RBRACKET)) return new ArrayLit(new ArrayList<>());
                    return parseArrayLiteral();
                }
                if (match(TokType.LBRACE)) {
                    if (match(TokType.RBRACE)) return new ObjectLit(new ArrayList<>(), new ArrayList<>());
                    return parseObjectLiteral();
                }

                if (match(TokType.FUNCTION)) {
                    return parseFunctionLiteral();
                }

                if (match(TokType.TEMPLATE)) {
                    return new TemplateString(previous().text);
                }

                if (check(TokType.IDENT)) {
                    Token first = advance();
                    return new IdentRef(first.text);
                }

                throw new ScriptParseException("Неожиданный токен: " + peek(), peek().pos);
            } finally {
                depth--;
            }
        }

        private Expr parseFunctionLiteral() {
            expect(TokType.LPAREN, "Ожидалась '('");
            List<String> params = new ArrayList<>();
            if (!check(TokType.RPAREN)) {
                do {
                    params.add(expect(TokType.IDENT, "Ожидалось имя параметра").text);
                } while (match(TokType.COMMA));
            }
            expect(TokType.RPAREN, "Ожидалась ')'");
            expect(TokType.LBRACE, "Ожидалась '{'");
            List<Stmt> body = new ArrayList<>();
            while (!check(TokType.RBRACE)) body.add(parseStmt());
            expect(TokType.RBRACE, "Ожидалась '}'");

            return new FunctionLit(new FunctionDef("", params, body));
        }

        private Expr parseArrayLiteral() {
            List<Expr> items = new ArrayList<>();
            items.add(parseExpr());
            while (match(TokType.COMMA)) {
                if (check(TokType.RBRACKET)) break;
                items.add(parseExpr());
            }
            expect(TokType.RBRACKET, "Ожидалась ']'");
            return new ArrayLit(items);
        }

        private Expr parseObjectLiteral() {
            List<String> keys = new ArrayList<>();
            List<Expr> values = new ArrayList<>();
            parseObjectEntry(keys, values);
            while (match(TokType.COMMA)) {
                if (check(TokType.RBRACE)) break;
                parseObjectEntry(keys, values);
            }
            expect(TokType.RBRACE, "Ожидалась '}'");
            return new ObjectLit(keys, values);
        }

        private void parseObjectEntry(List<String> keys, List<Expr> values) {
            Token key;
            if (check(TokType.IDENT)) key = advance();
            else if (check(TokType.STRING)) key = advance();
            else throw new ScriptParseException("Ожидался ключ объекта", peek().pos);
            expect(TokType.COLON, "Ожидался ':'");
            Expr value = parseExpr();
            keys.add(key.text);
            values.add(value);
        }

        private List<Expr> parseArgs() {
            List<Expr> args = new ArrayList<>();
            if (!check(TokType.RPAREN)) {
                args.add(parseExpr());
                while (match(TokType.COMMA)) {
                    if (check(TokType.RPAREN)) break;
                    args.add(parseExpr());
                }
            }
            return args;
        }

        private boolean check(TokType t) { return peek().type == t; }
        private boolean match(TokType t) { if (check(t)) { advance(); return true; } return false; }
        private Token expect(TokType t, String msg) { if (check(t)) return advance(); throw new ScriptParseException(msg + ", получено " + peek(), peek().pos); }
        private Token advance() { return tokens.get(pos++); }
        private Token peek() { return pos < tokens.size() ? tokens.get(pos) : tokens.get(tokens.size() - 1); }
        private Token previous() { return tokens.get(pos - 1); }
    }

    // ========================================================================
    // ИНТЕРПРЕТАТОР
    // ========================================================================

    private static final class ExecResult {
        enum Kind { RETURN, BREAK, CONTINUE }
        final Kind kind;
        final Value value;
        ExecResult(Kind kind, Value value) { this.kind = kind; this.value = value; }
    }

    private static final class Interpreter {
        private final ScriptContext ctx;
        private final Map<String, Value> locals = new HashMap<>();
        private final Map<String, FunctionDef> functions;
        private final int maxSteps;
        private int steps = 0;
        private static final int MAX_LOOP_ITERATIONS = 100_000;

        Interpreter(ScriptContext ctx, int maxSteps, Map<String, FunctionDef> functions) {
            this.ctx = ctx;
            this.maxSteps = maxSteps;
            this.functions = functions;
        }

        ExecResult execBlock(List<Stmt> stmts) {
            for (Stmt s : stmts) {
                ExecResult r = exec(s);
                if (r != null) return r;
            }
            return null;
        }

        private ExecResult exec(Stmt s) {
            step();

            if (s instanceof IfStmt) {
                IfStmt is = (IfStmt) s;
                if (eval(is.cond).truthy()) return execBlock(is.thenB);
                else if (is.elseB != null) return execBlock(is.elseB);
                return null;
            }
            if (s instanceof WhileStmt) {
                return execWhile((WhileStmt) s);
            }
            if (s instanceof DoWhileStmt) {
                return execDoWhile((DoWhileStmt) s);
            }
            if (s instanceof ForStmt) {
                return execFor((ForStmt) s);
            }
            if (s instanceof ForOfStmt) {
                return execForOf((ForOfStmt) s);
            }
            if (s instanceof ForInStmt) {
                return execForIn((ForInStmt) s);
            }
            if (s instanceof SwitchStmt) {
                return execSwitch((SwitchStmt) s);
            }
            if (s instanceof TryCatchStmt) {
                return execTryCatch((TryCatchStmt) s);
            }
            if (s instanceof ReturnStmt) {
                Expr expr = ((ReturnStmt) s).value;
                Value v = expr == null ? Value.NULL : eval(expr);
                return new ExecResult(ExecResult.Kind.RETURN, v);
            }
            if (s instanceof VarStmt) {
                VarStmt vs = (VarStmt) s;
                Expr expr = vs.value;
                Value v = expr == null ? Value.NULL : eval(expr);
                locals.put(vs.name, v);
                return null;
            }
            if (s instanceof FuncDefStmt) {
                return null;
            }
            if (s instanceof CallStmt) {
                CallStmt cs = (CallStmt) s;
                evalCall(cs.name, cs.args);
                return null;
            }
            if (s instanceof BreakStmt) {
                return new ExecResult(ExecResult.Kind.BREAK, null);
            }
            if (s instanceof ContinueStmt) {
                return new ExecResult(ExecResult.Kind.CONTINUE, null);
            }
            throw new ScriptRuntimeException("Неизвестный statement: " + s.getClass().getSimpleName());
        }

        private ExecResult execWhile(WhileStmt ws) {
            int iterations = 0;
            while (eval(ws.cond).truthy()) {
                iterations++;
                if (iterations > MAX_LOOP_ITERATIONS) {
                    throw new ScriptRuntimeException("While цикл превысил лимит итераций");
                }
                ExecResult r = execBlock(ws.body);
                if (r != null) {
                    if (r.kind == ExecResult.Kind.RETURN) return r;
                    if (r.kind == ExecResult.Kind.BREAK) break;
                }
            }
            return null;
        }

        private ExecResult execDoWhile(DoWhileStmt dws) {
            int iterations = 0;
            do {
                iterations++;
                if (iterations > MAX_LOOP_ITERATIONS) {
                    throw new ScriptRuntimeException("Do-while цикл превысил лимит итераций");
                }
                ExecResult r = execBlock(dws.body);
                if (r != null) {
                    if (r.kind == ExecResult.Kind.RETURN) return r;
                    if (r.kind == ExecResult.Kind.BREAK) break;
                }
            } while (eval(dws.cond).truthy());
            return null;
        }

        private ExecResult execFor(ForStmt fs) {
            int iterations = 0;
            Map<String, Value> savedLocals = new HashMap<>(locals);

            try {
                if (fs.init != null) {
                    if (fs.varName != null) {
                        locals.put(fs.varName, eval(fs.init));
                    } else {
                        eval(fs.init);
                    }
                }

                while (fs.cond == null || eval(fs.cond).truthy()) {
                    iterations++;
                    if (iterations > MAX_LOOP_ITERATIONS) {
                        throw new ScriptRuntimeException("For цикл превысил лимит итераций");
                    }

                    ExecResult r = execBlock(fs.body);
                    if (r == null) {
                        if (fs.update != null) eval(fs.update);
                    } else {
                        if (r.kind == ExecResult.Kind.RETURN) return r;
                        if (r.kind == ExecResult.Kind.BREAK) break;
                        if (r.kind == ExecResult.Kind.CONTINUE) {
                            if (fs.update != null) eval(fs.update);
                        }
                    }
                }
            } finally {
                locals.clear();
                locals.putAll(savedLocals);
            }
            return null;
        }

        private ExecResult execForOf(ForOfStmt fs) {
            Value listVal = eval(fs.iterExpr);
            List<Value> items = listVal.asListOrEmpty();
            if (items.size() > MAX_LOOP_ITERATIONS) {
                throw new ScriptRuntimeException("Список слишком большой (" + items.size() + " элементов)");
            }

            Value prevValue = locals.get(fs.varName);
            boolean hadPrev = locals.containsKey(fs.varName);
            try {
                for (Value item : items) {
                    step();
                    locals.put(fs.varName, item);
                    ExecResult r = execBlock(fs.body);
                    if (r != null) {
                        if (r.kind == ExecResult.Kind.RETURN) return r;
                        if (r.kind == ExecResult.Kind.BREAK) break;
                    }
                }
            } finally {
                if (hadPrev) locals.put(fs.varName, prevValue);
                else locals.remove(fs.varName);
            }
            return null;
        }

        private ExecResult execForIn(ForInStmt fs) {
            Value objVal = eval(fs.objExpr);
            Map<String, Value> map = objVal.asMapOrEmpty();
            if (map.size() > MAX_LOOP_ITERATIONS) {
                throw new ScriptRuntimeException("Объект слишком большой (" + map.size() + " свойств)");
            }

            Value prevValue = locals.get(fs.varName);
            boolean hadPrev = locals.containsKey(fs.varName);
            try {
                for (String key : map.keySet()) {
                    step();
                    locals.put(fs.varName, Value.of(key));
                    ExecResult r = execBlock(fs.body);
                    if (r != null) {
                        if (r.kind == ExecResult.Kind.RETURN) return r;
                        if (r.kind == ExecResult.Kind.BREAK) break;
                    }
                }
            } finally {
                if (hadPrev) locals.put(fs.varName, prevValue);
                else locals.remove(fs.varName);
            }
            return null;
        }

        private ExecResult execSwitch(SwitchStmt ss) {
            Value val = eval(ss.value);
            boolean matched = false;

            for (CaseClause cc : ss.cases) {
                Value caseVal = eval(cc.value);
                if (val.equals(caseVal)) {
                    matched = true;
                    ExecResult r = execBlock(cc.body);
                    if (r != null) {
                        if (r.kind == ExecResult.Kind.BREAK) break;
                        return r;
                    }
                }
            }

            if (!matched && ss.defaultCase != null) {
                return execBlock(ss.defaultCase);
            }
            return null;
        }

        private ExecResult execTryCatch(TryCatchStmt ts) {
            try {
                return execBlock(ts.tryBody);
            } catch (ScriptRuntimeException e) {
                locals.put(ts.catchVar, Value.of(e.getMessage()));
                return execBlock(ts.catchBody);
            }
        }

        private Value eval(Expr e) {
            step();

            if (e instanceof StringLit) return Value.of(((StringLit) e).v);
            if (e instanceof NumberLit) return Value.of(((NumberLit) e).v);
            if (e instanceof BoolLit) return Value.of(((BoolLit) e).v);
            if (e instanceof NullLit) return Value.NULL;

            if (e instanceof IdentRef) {
                String name = ((IdentRef) e).name;
                if (locals.containsKey(name)) return locals.get(name);
                if (ctx != null) return ctx.getGlobal(name);
                return Value.NULL;
            }

            if (e instanceof FieldAccess) {
                FieldAccess fa = (FieldAccess) e;
                Value base = eval(fa.base);
                if (base.type != Value.Type.MAP) return Value.NULL;
                return base.asMapOrEmpty().getOrDefault(fa.name, Value.NULL);
            }

            if (e instanceof OptionalChain) {
                OptionalChain oc = (OptionalChain) e;
                Value base = eval(oc.base);
                if (base == null || base.isNull()) return Value.NULL;
                if (base.type == Value.Type.MAP) {
                    return base.asMapOrEmpty().getOrDefault(oc.field, Value.NULL);
                }
                return Value.NULL;
            }

            if (e instanceof IndexExpr) {
                Value base = eval(((IndexExpr) e).base);
                Value idx = eval(((IndexExpr) e).index);

                if (base.type == Value.Type.LIST) {
                    int i = idx.asIntOrZero();
                    List<Value> list = base.asListOrEmpty();
                    if (i < 0 || i >= list.size()) return Value.NULL;
                    return list.get(i);
                }
                if (base.type == Value.Type.MAP) {
                    String key = idx.asStringOrNull();
                    if (key == null) return Value.NULL;
                    return base.asMapOrEmpty().getOrDefault(key, Value.NULL);
                }
                return Value.NULL;
            }

            if (e instanceof ArrayLit) {
                List<Value> items = new ArrayList<>();
                for (Expr item : ((ArrayLit) e).items) items.add(eval(item));
                return Value.ofList(items);
            }

            if (e instanceof ObjectLit) {
                ObjectLit ol = (ObjectLit) e;
                Map<String, Value> m = new HashMap<>();
                for (int i = 0; i < ol.keys.size(); i++) {
                    m.put(ol.keys.get(i), eval(ol.values.get(i)));
                }
                return Value.ofMap(m);
            }

            if (e instanceof Ternary) {
                Ternary t = (Ternary) e;
                return eval(t.cond).truthy() ? eval(t.thenE) : eval(t.elseE);
            }

            if (e instanceof MethodCall) {
                MethodCall mc = (MethodCall) e;
                Value target = eval(mc.target);
                List<Value> args = new ArrayList<>();
                for (Expr arg : mc.args) args.add(eval(arg));
                return evalMethod(target, mc.method, args);
            }

            if (e instanceof FuncCall) {
                FuncCall fc = (FuncCall) e;
                return evalCall(fc.name, fc.args);
            }

            if (e instanceof FunctionLit) {
                return Value.ofFunction(((FunctionLit) e).func);
            }

            if (e instanceof SpreadExpr) {
                Value target = eval(((SpreadExpr) e).target);
                if (target.type == Value.Type.LIST) {
                    return Value.ofList(new ArrayList<>(target.asListOrEmpty()));
                }
                if (target.type == Value.Type.MAP) {
                    return Value.ofMap(new HashMap<>(target.asMapOrEmpty()));
                }
                return target;
            }

            if (e instanceof NullishCoalesce) {
                NullishCoalesce nc = (NullishCoalesce) e;
                Value l = eval(nc.l);
                return l.isNull() ? eval(nc.r) : l;
            }

            if (e instanceof DestructArray) {
                DestructArray da = (DestructArray) e;
                Value source = eval(da.source);
                List<Value> list = source.asListOrEmpty();
                for (int i = 0; i < da.vars.size() && i < list.size(); i++) {
                    locals.put(da.vars.get(i), list.get(i));
                }
                return Value.NULL;
            }

            if (e instanceof DestructObject) {
                DestructObject d = (DestructObject) e;
                Value source = eval(d.source);
                Map<String, Value> map = source.asMapOrEmpty();
                for (Map.Entry<String, String> entry : d.vars.entrySet()) {
                    locals.put(entry.getValue(), map.getOrDefault(entry.getKey(), Value.NULL));
                }
                return Value.NULL;
            }

            if (e instanceof TemplateString) {
                return Value.of(((TemplateString) e).value);
            }

            if (e instanceof UnaryOp) {
                UnaryOp uo = (UnaryOp) e;
                switch (uo.op) {
                    case "++": {
                        if (uo.expr instanceof IdentRef) {
                            String name = ((IdentRef) uo.expr).name;
                            Value v = getVariable(name);
                            double num = v.asNumberOrZero();
                            setVariable(name, Value.of(num + 1));
                            return getVariable(name);
                        }
                        break;
                    }
                    case "--": {
                        if (uo.expr instanceof IdentRef) {
                            String name = ((IdentRef) uo.expr).name;
                            Value v = getVariable(name);
                            double num = v.asNumberOrZero();
                            setVariable(name, Value.of(num - 1));
                            return getVariable(name);
                        }
                        break;
                    }
                    case "post++": {
                        if (uo.expr instanceof IdentRef) {
                            String name = ((IdentRef) uo.expr).name;
                            Value v = getVariable(name);
                            double num = v.asNumberOrZero();
                            setVariable(name, Value.of(num + 1));
                            return v;
                        }
                        break;
                    }
                    case "post--": {
                        if (uo.expr instanceof IdentRef) {
                            String name = ((IdentRef) uo.expr).name;
                            Value v = getVariable(name);
                            double num = v.asNumberOrZero();
                            setVariable(name, Value.of(num - 1));
                            return v;
                        }
                        break;
                    }
                    case "-":
                        return Value.of(-eval(uo.expr).asNumberOrZero());
                }
                return eval(uo.expr);
            }

            if (e instanceof BinaryOp) {
                BinaryOp bo = (BinaryOp) e;
                Value l = eval(bo.l);
                Value r = eval(bo.r);
                String op = bo.op;

                switch (op) {
                    case "+": {
                        if (l.type == Value.Type.STRING || r.type == Value.Type.STRING) {
                            String left = l.asStringOrNull();
                            String right = r.asStringOrNull();
                            return Value.of((left == null ? "" : left) + (right == null ? "" : right));
                        }
                        return Value.of(l.asNumberOrZero() + r.asNumberOrZero());
                    }
                    case "-": return Value.of(l.asNumberOrZero() - r.asNumberOrZero());
                    case "*": return Value.of(l.asNumberOrZero() * r.asNumberOrZero());
                    case "/": {
                        double divisor = r.asNumberOrZero();
                        if (divisor == 0) throw new ScriptRuntimeException("Деление на ноль");
                        return Value.of(l.asNumberOrZero() / divisor);
                    }
                    case "%": return Value.of(l.asNumberOrZero() % r.asNumberOrZero());
                    case "**": return Value.of(Math.pow(l.asNumberOrZero(), r.asNumberOrZero()));
                }
                return Value.NULL;
            }

            if (e instanceof InOp) {
                InOp io = (InOp) e;
                Value item = eval(io.item);
                Value list = eval(io.list);
                if (list.type == Value.Type.LIST) {
                    return Value.of(list.asListOrEmpty().contains(item));
                }
                if (list.type == Value.Type.MAP) {
                    String key = item.asStringOrNull();
                    return Value.of(key != null && list.asMapOrEmpty().containsKey(key));
                }
                return Value.FALSE;
            }

            if (e instanceof InstanceOfOp) {
                InstanceOfOp io = (InstanceOfOp) e;
                Value obj = eval(io.obj);
                String type = io.type;
                switch (type) {
                    case "Array": return Value.of(obj.type == Value.Type.LIST);
                    case "Object": return Value.of(obj.type == Value.Type.MAP);
                    case "String": return Value.of(obj.type == Value.Type.STRING);
                    case "Number": return Value.of(obj.type == Value.Type.NUMBER);
                    case "Boolean": return Value.of(obj.type == Value.Type.BOOL);
                    case "Function": return Value.of(obj.type == Value.Type.FUNCTION);
                    default: return Value.FALSE;
                }
            }

            if (e instanceof TypeOfOp) {
                TypeOfOp to = (TypeOfOp) e;
                Value v = eval(to.expr);
                switch (v.type) {
                    case STRING: return Value.of("string");
                    case NUMBER: return Value.of("number");
                    case BOOL: return Value.of("boolean");
                    case NULL:
                    case MAP:
                    case LIST:
                        return Value.of("object");
                    case FUNCTION: return Value.of("function");
                    default: return Value.of("undefined");
                }
            }

            if (e instanceof Cmp) {
                Cmp c = (Cmp) e;
                Value l = eval(c.l);
                Value r = eval(c.r);

                switch (c.op) {
                    case EQ:
                    case STRICT_EQ:
                        return Value.of(l.equals(r));
                    case NEQ:
                    case STRICT_NEQ:
                        return Value.of(!l.equals(r));
                    case GT: return Value.of(l.asNumberOrZero() > r.asNumberOrZero());
                    case LT: return Value.of(l.asNumberOrZero() < r.asNumberOrZero());
                    case GTE: return Value.of(l.asNumberOrZero() >= r.asNumberOrZero());
                    case LTE: return Value.of(l.asNumberOrZero() <= r.asNumberOrZero());
                    case CONTAINS: {
                        if (l.type == Value.Type.LIST) return Value.of(l.asListOrEmpty().contains(r));
                        String ls = l.asStringOrNull();
                        String rs = r.asStringOrNull();
                        return (ls == null || rs == null) ? Value.FALSE : Value.of(ls.contains(rs));
                    }
                    case STARTS_WITH: {
                        String ls = l.asStringOrNull();
                        String rs = r.asStringOrNull();
                        return (ls == null || rs == null) ? Value.FALSE : Value.of(ls.startsWith(rs));
                    }
                    case ENDS_WITH: {
                        String ls = l.asStringOrNull();
                        String rs = r.asStringOrNull();
                        return (ls == null || rs == null) ? Value.FALSE : Value.of(ls.endsWith(rs));
                    }
                    case MATCHES: {
                        String ls = l.asStringOrNull();
                        String rs = r.asStringOrNull();
                        if (ls == null || rs == null) return Value.FALSE;
                        try {
                            return Value.of(Pattern.compile(rs).matcher(ls).matches());
                        } catch (Exception ex) { return Value.FALSE; }
                    }
                    default: return Value.FALSE;
                }
            }

            if (e instanceof And) {
                Value l = eval(((And) e).l);
                if (!l.truthy()) return Value.FALSE;
                return Value.of(eval(((And) e).r).truthy());
            }

            if (e instanceof Or) {
                Value l = eval(((Or) e).l);
                if (l.truthy()) return Value.TRUE;
                return Value.of(eval(((Or) e).r).truthy());
            }

            if (e instanceof Not) {
                return Value.of(!eval(((Not) e).e).truthy());
            }

            throw new ScriptRuntimeException("Неизвестное выражение: " + e.getClass().getSimpleName());
        }

        private Value getVariable(String name) {
            if (locals.containsKey(name)) return locals.get(name);
            if (ctx != null) return ctx.getGlobal(name);
            return Value.NULL;
        }

        private void setVariable(String name, Value value) {
            if (locals.containsKey(name)) {
                locals.put(name, value);
            } else if (ctx != null) {
                ctx.set(name, value);
            }
        }

        private Value evalMethod(Value target, String method, List<Value> args) {
            switch (method) {
                case "toLowerCase": {
                    String s = target.asStringOrNull();
                    return s == null ? Value.NULL : Value.of(s.toLowerCase());
                }
                case "toUpperCase": {
                    String s = target.asStringOrNull();
                    return s == null ? Value.NULL : Value.of(s.toUpperCase());
                }
                case "trim": {
                    String s = target.asStringOrNull();
                    return s == null ? Value.NULL : Value.of(s.trim());
                }
                case "length": {
                    if (target.type == Value.Type.LIST) {
                        return Value.of(target.asListOrEmpty().size());
                    }
                    if (target.type == Value.Type.MAP) {
                        return Value.of(target.asMapOrEmpty().size());
                    }
                    String s = target.asStringOrNull();
                    return Value.of(s == null ? 0 : s.length());
                }
                case "contains": {
                    if (args.isEmpty()) return Value.FALSE;
                    Value arg = args.get(0);
                    if (target.type == Value.Type.LIST) {
                        return Value.of(target.asListOrEmpty().contains(arg));
                    }
                    if (target.type == Value.Type.MAP) {
                        String key = arg.asStringOrNull();
                        return Value.of(key != null && target.asMapOrEmpty().containsKey(key));
                    }
                    String ts = target.asStringOrNull();
                    String as = arg.asStringOrNull();
                    if (ts == null || as == null) return Value.FALSE;
                    return Value.of(ts.contains(as));
                }
                case "startsWith": {
                    if (args.isEmpty()) return Value.FALSE;
                    String ts = target.asStringOrNull();
                    String as = args.get(0).asStringOrNull();
                    if (ts == null || as == null) return Value.FALSE;
                    return Value.of(ts.startsWith(as));
                }
                case "endsWith": {
                    if (args.isEmpty()) return Value.FALSE;
                    String ts = target.asStringOrNull();
                    String as = args.get(0).asStringOrNull();
                    if (ts == null || as == null) return Value.FALSE;
                    return Value.of(ts.endsWith(as));
                }
                case "indexOf": {
                    if (args.isEmpty()) return Value.of(-1);
                    String ts = target.asStringOrNull();
                    String as = args.get(0).asStringOrNull();
                    if (ts == null || as == null) return Value.of(-1);
                    return Value.of(ts.indexOf(as));
                }
                case "lastIndexOf": {
                    if (args.isEmpty()) return Value.of(-1);
                    String ts = target.asStringOrNull();
                    String as = args.get(0).asStringOrNull();
                    if (ts == null || as == null) return Value.of(-1);
                    return Value.of(ts.lastIndexOf(as));
                }
                case "substring": {
                    String ts = target.asStringOrNull();
                    if (ts == null || args.isEmpty()) return Value.NULL;
                    int start = args.get(0).asIntOrZero();
                    int end = args.size() > 1 ? args.get(1).asIntOrZero() : ts.length();
                    return Value.of(ts.substring(Math.min(start, ts.length()), Math.min(end, ts.length())));
                }
                case "slice": {
                    String ts = target.asStringOrNull();
                    if (ts == null) return target;
                    int start = !args.isEmpty() ? args.get(0).asIntOrZero() : 0;
                    int end = args.size() > 1 ? args.get(1).asIntOrZero() : ts.length();
                    if (start < 0) start = ts.length() + start;
                    if (end < 0) end = ts.length() + end;
                    return Value.of(ts.substring(Math.max(0, start), Math.min(ts.length(), end)));
                }
                case "split": {
                    String ts = target.asStringOrNull();
                    if (ts == null || args.isEmpty()) return Value.ofList(Collections.emptyList());
                    String delimiter = args.get(0).asStringOrNull();
                    if (delimiter == null) return Value.ofList(Collections.emptyList());
                    String[] parts = ts.split(Pattern.quote(delimiter));
                    List<Value> result = new ArrayList<>();
                    for (String part : parts) result.add(Value.of(part));
                    return Value.ofList(result);
                }
                case "repeat": {
                    String ts = target.asStringOrNull();
                    if (ts == null || args.isEmpty()) return Value.of("");
                    int count = Math.min(args.get(0).asIntOrZero(), 1000);
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < count; i++) result.append(ts);
                    return Value.of(result.toString());
                }
                case "padStart": {
                    String ts = target.asStringOrNull();
                    if (ts == null || args.isEmpty()) return target;
                    int len = Math.min(args.get(0).asIntOrZero(), 1000);
                    String pad = args.size() > 1 ? args.get(1).asStringOrNull() : " ";
                    if (pad == null || pad.isEmpty()) pad = " ";
                    StringBuilder result = new StringBuilder();
                    while (result.length() + ts.length() < len) result.append(pad);
                    result.append(ts);
                    return Value.of(result.toString());
                }
                case "padEnd": {
                    String ts = target.asStringOrNull();
                    if (ts == null || args.isEmpty()) return target;
                    int len = Math.min(args.get(0).asIntOrZero(), 1000);
                    String pad = args.size() > 1 ? args.get(1).asStringOrNull() : " ";
                    if (pad == null || pad.isEmpty()) pad = " ";
                    StringBuilder result = new StringBuilder(ts);
                    while (result.length() < len) result.append(pad);
                    return Value.of(result.toString());
                }
                case "replace": {
                    String ts = target.asStringOrNull();
                    if (ts == null || args.size() < 2) return target;
                    String from = args.get(0).asStringOrNull();
                    String to = args.get(1).asStringOrNull();
                    if (from == null || to == null) return target;
                    return Value.of(ts.replace(from, to));
                }
                case "push": {
                    List<Value> list = new ArrayList<>(target.asListOrEmpty());
                    list.addAll(args);
                    return Value.ofList(list);
                }
                case "pop": {
                    List<Value> list = new ArrayList<>(target.asListOrEmpty());
                    if (list.isEmpty()) return Value.NULL;
                    return list.remove(list.size() - 1);
                }
                case "shift": {
                    List<Value> list = new ArrayList<>(target.asListOrEmpty());
                    if (list.isEmpty()) return Value.NULL;
                    return list.remove(0);
                }
                case "unshift": {
                    List<Value> list = new ArrayList<>(args);
                    list.addAll(target.asListOrEmpty());
                    return Value.ofList(list);
                }
                case "concat": {
                    List<Value> result = new ArrayList<>(target.asListOrEmpty());
                    for (Value arg : args) {
                        if (arg.type == Value.Type.LIST) {
                            result.addAll(arg.asListOrEmpty());
                        } else {
                            result.add(arg);
                        }
                    }
                    return Value.ofList(result);
                }
                case "join": {
                    List<Value> list = target.asListOrEmpty();
                    if (list.isEmpty()) return Value.of("");
                    String delimiter = args.isEmpty() ? "" : args.get(0).asStringOrNull();
                    if (delimiter == null) delimiter = "";
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) result.append(delimiter);
                        String s = list.get(i).asStringOrNull();
                        result.append(s == null ? "" : s);
                    }
                    return Value.of(result.toString());
                }
                case "map": {
                    if (args.isEmpty() || target.type != Value.Type.LIST) return target;
                    FunctionDef func = args.get(0).asFunctionOrNull();
                    if (func == null) return target;
                    List<Value> list = target.asListOrEmpty();
                    List<Value> result = new ArrayList<>();
                    Map<String, Value> oldLocals = new HashMap<>(locals);
                    try {
                        for (Value item : list) {
                            locals.clear();
                            locals.putAll(oldLocals);
                            if (!func.params.isEmpty()) locals.put(func.params.get(0), item);
                            ExecResult r = execBlock(func.body);
                            result.add(r != null && r.kind == ExecResult.Kind.RETURN ? r.value : Value.NULL);
                        }
                    } finally {
                        locals.clear();
                        locals.putAll(oldLocals);
                    }
                    return Value.ofList(result);
                }
                case "filter": {
                    if (args.isEmpty() || target.type != Value.Type.LIST) return target;
                    FunctionDef func = args.get(0).asFunctionOrNull();
                    if (func == null) return target;
                    List<Value> list = target.asListOrEmpty();
                    List<Value> result = new ArrayList<>();
                    Map<String, Value> oldLocals = new HashMap<>(locals);
                    try {
                        for (Value item : list) {
                            locals.clear();
                            locals.putAll(oldLocals);
                            if (!func.params.isEmpty()) locals.put(func.params.get(0), item);
                            ExecResult r = execBlock(func.body);
                            Value condition = r != null && r.kind == ExecResult.Kind.RETURN ? r.value : Value.FALSE;
                            if (condition.truthy()) result.add(item);
                        }
                    } finally {
                        locals.clear();
                        locals.putAll(oldLocals);
                    }
                    return Value.ofList(result);
                }
                case "reduce": {
                    if (args.size() < 2 || target.type != Value.Type.LIST) return target;
                    FunctionDef func = args.get(0).asFunctionOrNull();
                    if (func == null) return target;
                    Value initial = args.get(1);
                    List<Value> list = target.asListOrEmpty();
                    Value accumulator = initial;
                    Map<String, Value> oldLocals = new HashMap<>(locals);
                    try {
                        for (Value item : list) {
                            locals.clear();
                            locals.putAll(oldLocals);
                            if (!func.params.isEmpty()) locals.put(func.params.get(0), accumulator);
                            if (func.params.size() > 1) locals.put(func.params.get(1), item);
                            ExecResult r = execBlock(func.body);
                            accumulator = r != null && r.kind == ExecResult.Kind.RETURN ? r.value : Value.NULL;
                        }
                    } finally {
                        locals.clear();
                        locals.putAll(oldLocals);
                    }
                    return accumulator;
                }
                case "forEach": {
                    if (args.isEmpty() || target.type != Value.Type.LIST) return target;
                    FunctionDef func = args.get(0).asFunctionOrNull();
                    if (func == null) return target;
                    List<Value> list = target.asListOrEmpty();
                    Map<String, Value> oldLocals = new HashMap<>(locals);
                    try {
                        for (Value item : list) {
                            locals.clear();
                            locals.putAll(oldLocals);
                            if (!func.params.isEmpty()) locals.put(func.params.get(0), item);
                            execBlock(func.body);
                        }
                    } finally {
                        locals.clear();
                        locals.putAll(oldLocals);
                    }
                    return target;
                }
                case "find": {
                    if (args.isEmpty() || target.type != Value.Type.LIST) return Value.NULL;
                    FunctionDef func = args.get(0).asFunctionOrNull();
                    if (func == null) return Value.NULL;
                    List<Value> list = target.asListOrEmpty();
                    Map<String, Value> oldLocals = new HashMap<>(locals);
                    try {
                        for (Value item : list) {
                            locals.clear();
                            locals.putAll(oldLocals);
                            if (!func.params.isEmpty()) locals.put(func.params.get(0), item);
                            ExecResult r = execBlock(func.body);
                            Value condition = r != null && r.kind == ExecResult.Kind.RETURN ? r.value : Value.FALSE;
                            if (condition.truthy()) return item;
                        }
                    } finally {
                        locals.clear();
                        locals.putAll(oldLocals);
                    }
                    return Value.NULL;
                }
                case "findIndex": {
                    if (args.isEmpty() || target.type != Value.Type.LIST) return Value.of(-1);
                    FunctionDef func = args.get(0).asFunctionOrNull();
                    if (func == null) return Value.of(-1);
                    List<Value> list = target.asListOrEmpty();
                    Map<String, Value> oldLocals = new HashMap<>(locals);
                    try {
                        for (int i = 0; i < list.size(); i++) {
                            locals.clear();
                            locals.putAll(oldLocals);
                            if (!func.params.isEmpty()) locals.put(func.params.get(0), list.get(i));
                            ExecResult r = execBlock(func.body);
                            Value condition = r != null && r.kind == ExecResult.Kind.RETURN ? r.value : Value.FALSE;
                            if (condition.truthy()) return Value.of(i);
                        }
                    } finally {
                        locals.clear();
                        locals.putAll(oldLocals);
                    }
                    return Value.of(-1);
                }
                case "some": {
                    if (args.isEmpty() || target.type != Value.Type.LIST) return Value.FALSE;
                    FunctionDef func = args.get(0).asFunctionOrNull();
                    if (func == null) return Value.FALSE;
                    List<Value> list = target.asListOrEmpty();
                    Map<String, Value> oldLocals = new HashMap<>(locals);
                    try {
                        for (Value item : list) {
                            locals.clear();
                            locals.putAll(oldLocals);
                            if (!func.params.isEmpty()) locals.put(func.params.get(0), item);
                            ExecResult r = execBlock(func.body);
                            Value condition = r != null && r.kind == ExecResult.Kind.RETURN ? r.value : Value.FALSE;
                            if (condition.truthy()) return Value.TRUE;
                        }
                    } finally {
                        locals.clear();
                        locals.putAll(oldLocals);
                    }
                    return Value.FALSE;
                }
                case "every": {
                    if (args.isEmpty() || target.type != Value.Type.LIST) return Value.TRUE;
                    FunctionDef func = args.get(0).asFunctionOrNull();
                    if (func == null) return Value.FALSE;
                    List<Value> list = target.asListOrEmpty();
                    Map<String, Value> oldLocals = new HashMap<>(locals);
                    try {
                        for (Value item : list) {
                            locals.clear();
                            locals.putAll(oldLocals);
                            if (!func.params.isEmpty()) locals.put(func.params.get(0), item);
                            ExecResult r = execBlock(func.body);
                            Value condition = r != null && r.kind == ExecResult.Kind.RETURN ? r.value : Value.FALSE;
                            if (!condition.truthy()) return Value.FALSE;
                        }
                    } finally {
                        locals.clear();
                        locals.putAll(oldLocals);
                    }
                    return Value.TRUE;
                }
                case "sort": {
                    List<Value> list = new ArrayList<>(target.asListOrEmpty());
                    if (args.isEmpty()) {
                        list.sort((a, b) -> {
                            String sa = a.asStringOrNull();
                            String sb = b.asStringOrNull();
                            if (sa == null && sb == null) return 0;
                            if (sa == null) return -1;
                            if (sb == null) return 1;
                            return sa.compareTo(sb);
                        });
                    } else {
                        FunctionDef func = args.get(0).asFunctionOrNull();
                        if (func != null) {
                            final Map<String, Value> oldLocals = new HashMap<>(locals);
                            list.sort((a, b) -> {
                                locals.clear();
                                locals.putAll(oldLocals);
                                if (!func.params.isEmpty()) locals.put(func.params.get(0), a);
                                if (func.params.size() > 1) locals.put(func.params.get(1), b);
                                ExecResult r = execBlock(func.body);
                                Value result = r != null && r.kind == ExecResult.Kind.RETURN ? r.value : Value.of(0);
                                return (int) result.asNumberOrZero();
                            });
                        }
                    }
                    return Value.ofList(list);
                }
                case "reverse": {
                    List<Value> list = new ArrayList<>(target.asListOrEmpty());
                    Collections.reverse(list);
                    return Value.ofList(list);
                }
                case "flat": {
                    int depth = args.isEmpty() ? 1 : Math.min(args.get(0).asIntOrZero(), 10);
                    List<Value> result = new ArrayList<>();
                    flattenArray(target.asListOrEmpty(), result, depth);
                    return Value.ofList(result);
                }
                case "keys": {
                    Map<String, Value> map = target.asMapOrEmpty();
                    List<Value> keys = new ArrayList<>();
                    for (String key : map.keySet()) keys.add(Value.of(key));
                    return Value.ofList(keys);
                }
                case "values": {
                    return Value.ofList(new ArrayList<>(target.asMapOrEmpty().values()));
                }
                case "entries": {
                    Map<String, Value> map = target.asMapOrEmpty();
                    List<Value> entries = new ArrayList<>();
                    for (Map.Entry<String, Value> entry : map.entrySet()) {
                        Map<String, Value> pair = new HashMap<>();
                        pair.put("key", Value.of(entry.getKey()));
                        pair.put("value", entry.getValue());
                        entries.add(Value.ofMap(pair));
                    }
                    return Value.ofList(entries);
                }
                case "hasOwnProperty": {
                    if (args.isEmpty()) return Value.FALSE;
                    String key = args.get(0).asStringOrNull();
                    if (key == null) return Value.FALSE;
                    return Value.of(target.asMapOrEmpty().containsKey(key));
                }
                default:
                    throw new ScriptRuntimeException("Метод '" + method + "' не поддерживается");
            }
        }

        private void flattenArray(List<Value> source, List<Value> target, int depth) {
            for (Value v : source) {
                if (depth > 0 && v.type == Value.Type.LIST) {
                    flattenArray(v.asListOrEmpty(), target, depth - 1);
                } else {
                    target.add(v);
                }
            }
        }

        private Value evalCall(String name, List<Expr> args) {
            List<Value> argValues = new ArrayList<>();
            for (Expr arg : args) argValues.add(eval(arg));

            switch (name) {
                case "print":
                case "console.log": {
                    StringBuilder msg = new StringBuilder();
                    for (Value v : argValues) {
                        if (msg.length() > 0) msg.append(' ');
                        String s = v.asStringOrNull();
                        msg.append(s == null ? "null" : s);
                    }
                    System.out.println("[Script] " + msg);
                    return Value.NULL;
                }
                case "console.warn": {
                    StringBuilder msg = new StringBuilder();
                    for (Value v : argValues) {
                        if (msg.length() > 0) msg.append(' ');
                        String s = v.asStringOrNull();
                        msg.append(s == null ? "null" : s);
                    }
                    System.out.println("[Script WARN] " + msg);
                    return Value.NULL;
                }
                case "console.error": {
                    StringBuilder msg = new StringBuilder();
                    for (Value v : argValues) {
                        if (msg.length() > 0) msg.append(' ');
                        String s = v.asStringOrNull();
                        msg.append(s == null ? "null" : s);
                    }
                    System.err.println("[Script ERROR] " + msg);
                    return Value.NULL;
                }
                case "parseInt": {
                    if (argValues.isEmpty()) return Value.of(0);
                    return Value.of((int) argValues.get(0).asNumberOrZero());
                }
                case "parseFloat": {
                    if (argValues.isEmpty()) return Value.of(0);
                    return Value.of(argValues.get(0).asNumberOrZero());
                }
                case "isNaN": {
                    if (argValues.isEmpty()) return Value.FALSE;
                    return Value.of(Double.isNaN(argValues.get(0).asNumberOrZero()));
                }
                case "isFinite": {
                    if (argValues.isEmpty()) return Value.FALSE;
                    return Value.of(Double.isFinite(argValues.get(0).asNumberOrZero()));
                }
                case "encodeURI": {
                    if (argValues.isEmpty()) return Value.of("");
                    String s = argValues.get(0).asStringOrNull();
                    if (s == null) return Value.of("");
                    try {
                        return Value.of(java.net.URLEncoder.encode(s, "UTF-8"));
                    } catch (Exception ex) {
                        return Value.of(s);
                    }
                }
                case "decodeURI": {
                    if (argValues.isEmpty()) return Value.of("");
                    String s = argValues.get(0).asStringOrNull();
                    if (s == null) return Value.of("");
                    try {
                        return Value.of(java.net.URLDecoder.decode(s, "UTF-8"));
                    } catch (Exception ex) {
                        return Value.of(s);
                    }
                }
                case "Math": {
                    return evalMath(argValues);
                }
                case "Date": {
                    return evalDate(argValues);
                }
                case "JSON": {
                    return evalJson(argValues);
                }
                case "Set": {
                    return evalSet(argValues);
                }
                case "Map": {
                    return evalMap(argValues);
                }
                case "setTimeout": {
                    if (argValues.size() >= 2) {
                        FunctionDef func = argValues.get(0).asFunctionOrNull();
                        int delay = Math.min(argValues.get(1).asIntOrZero(), 60000);
                        if (func != null && delay > 0 && ctx != null) {
                            ctx.registerTimer(() -> {
                                Map<String, Value> oldLocals = new HashMap<>(locals);
                                try {
                                    execBlock(func.body);
                                } finally {
                                    locals.clear();
                                    locals.putAll(oldLocals);
                                }
                            }, delay, false);
                        }
                    }
                    return Value.NULL;
                }
                case "setInterval": {
                    if (argValues.size() >= 2) {
                        FunctionDef func = argValues.get(0).asFunctionOrNull();
                        int delay = Math.min(argValues.get(1).asIntOrZero(), 60000);
                        if (func != null && delay > 0 && ctx != null) {
                            ctx.registerTimer(() -> {
                                Map<String, Value> oldLocals = new HashMap<>(locals);
                                try {
                                    execBlock(func.body);
                                } finally {
                                    locals.clear();
                                    locals.putAll(oldLocals);
                                }
                            }, delay, true);
                        }
                    }
                    return Value.NULL;
                }
                case "clearTimeout":
                case "clearInterval": {
                    if (ctx != null) ctx.stopAllTimers();
                    return Value.NULL;
                }
            }

            FunctionDef func = functions.get(name);
            if (func == null) {
                throw new ScriptRuntimeException("Функция '" + name + "' не найдена");
            }

            if (argValues.size() != func.params.size()) {
                throw new ScriptRuntimeException("Функция '" + name + "' ожидает " + func.params.size() +
                        " аргументов, получено " + argValues.size());
            }

            Map<String, Value> oldLocals = new HashMap<>(locals);
            try {
                for (int i = 0; i < func.params.size(); i++) {
                    locals.put(func.params.get(i), argValues.get(i));
                }
                ExecResult result = execBlock(func.body);
                if (result == null) return Value.NULL;
                return result.kind == ExecResult.Kind.RETURN ? result.value : Value.NULL;
            } finally {
                locals.clear();
                locals.putAll(oldLocals);
            }
        }

        private Value evalMath(List<Value> args) {
            Map<String, Value> math = new HashMap<>();
            math.put("PI", Value.of(Math.PI));
            math.put("E", Value.of(Math.E));

            double x = args.isEmpty() ? 0 : args.get(0).asNumberOrZero();
            double y = args.size() > 1 ? args.get(1).asNumberOrZero() : 0;

            math.put("abs", Value.of(Math.abs(x)));
            math.put("ceil", Value.of(Math.ceil(x)));
            math.put("floor", Value.of(Math.floor(x)));
            math.put("round", Value.of(Math.round(x)));
            math.put("max", Value.of(Math.max(x, y)));
            math.put("min", Value.of(Math.min(x, y)));
            math.put("pow", Value.of(Math.pow(x, y)));
            math.put("sqrt", Value.of(Math.sqrt(x)));
            math.put("random", Value.of(Math.random()));
            math.put("sin", Value.of(Math.sin(x)));
            math.put("cos", Value.of(Math.cos(x)));
            math.put("tan", Value.of(Math.tan(x)));

            return Value.ofMap(math);
        }

        private Value evalDate(List<Value> args) {
            if (!args.isEmpty()) {
                String dateStr = args.get(0).asStringOrNull();
                if (dateStr != null) {
                    try {
                        LocalDateTime date = LocalDateTime.parse(dateStr);
                        return Value.ofDate(date);
                    } catch (DateTimeParseException ex) {
                        return Value.NULL;
                    }
                }
                return Value.ofDate(LocalDateTime.now());
            }
            return Value.ofDate(LocalDateTime.now());
        }

        private Value evalJson(List<Value> args) {
            Map<String, Value> json = new HashMap<>();

            if (!args.isEmpty()) {
                Value val = args.get(0);
                json.put("stringify", Value.ofFunction(new FunctionDef("stringify",
                        Collections.singletonList("obj"),
                        Collections.singletonList(new ReturnStmt(new StringLit(jsonToString(val)))))));
                json.put("parse", Value.ofFunction(new FunctionDef("parse",
                        Collections.singletonList("str"),
                        Collections.singletonList(new ReturnStmt(new StringLit(
                                val.asStringOrNull() == null ? "null" : val.asStringOrNull()
                        ))))));
            } else {
                json.put("stringify", Value.ofFunction(new FunctionDef("stringify",
                        Collections.singletonList("obj"), Collections.emptyList())));
                json.put("parse", Value.ofFunction(new FunctionDef("parse",
                        Collections.singletonList("str"), Collections.emptyList())));
            }

            return Value.ofMap(json);
        }

        private String jsonToString(Value v) {
            if (v == null || v.isNull()) return "null";
            switch (v.type) {
                case STRING: return "\"" + escapeJson(v.asStringOrNull()) + "\"";
                case NUMBER:
                case BOOL:
                    return v.toString();
                case LIST: {
                    List<String> items = new ArrayList<>();
                    for (Value item : v.asListOrEmpty()) items.add(jsonToString(item));
                    return "[" + String.join(", ", items) + "]";
                }
                case MAP: {
                    List<String> entries = new ArrayList<>();
                    for (Map.Entry<String, Value> entry : v.asMapOrEmpty().entrySet()) {
                        entries.add("\"" + entry.getKey() + "\": " + jsonToString(entry.getValue()));
                    }
                    return "{" + String.join(", ", entries) + "}";
                }
                default: return "null";
            }
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        private Value evalSet(List<Value> args) {
            Set<Value> set = new HashSet<>();
            if (!args.isEmpty() && args.get(0).type == Value.Type.LIST) {
                set.addAll(args.get(0).asListOrEmpty());
            }

            Map<String, Value> setObj = new HashMap<>();
            setObj.put("size", Value.of(set.size()));

            if (!args.isEmpty()) {
                Value val = args.get(0);
                setObj.put("add", Value.ofFunction(new FunctionDef("add",
                        Collections.singletonList("value"),
                        Collections.singletonList(new ReturnStmt(new BoolLit(set.add(val)))))));
                setObj.put("delete", Value.ofFunction(new FunctionDef("delete",
                        Collections.singletonList("value"),
                        Collections.singletonList(new ReturnStmt(new BoolLit(set.remove(val)))))));
                setObj.put("has", Value.ofFunction(new FunctionDef("has",
                        Collections.singletonList("value"),
                        Collections.singletonList(new ReturnStmt(new BoolLit(set.contains(val)))))));
            } else {
                setObj.put("add", Value.ofFunction(new FunctionDef("add",
                        Collections.singletonList("value"), Collections.emptyList())));
                setObj.put("delete", Value.ofFunction(new FunctionDef("delete",
                        Collections.singletonList("value"), Collections.emptyList())));
                setObj.put("has", Value.ofFunction(new FunctionDef("has",
                        Collections.singletonList("value"), Collections.emptyList())));
            }

            setObj.put("clear", Value.ofFunction(new FunctionDef("clear",
                    Collections.emptyList(),
                    Collections.singletonList(new ReturnStmt(new BoolLit(true))))));

            return Value.ofMap(setObj);
        }

        private Value evalMap(List<Value> args) {
            Map<Value, Value> map = new HashMap<>();

            Map<String, Value> mapObj = new HashMap<>();
            mapObj.put("size", Value.of(0));

            if (args.size() >= 2) {
                Value key = args.get(0);
                Value value = args.get(1);
                mapObj.put("set", Value.ofFunction(new FunctionDef("set",
                        Arrays.asList("key", "value"),
                        Collections.singletonList(new ReturnStmt(new BoolLit(map.put(key, value) != null))))));
                mapObj.put("get", Value.ofFunction(new FunctionDef("get",
                        Collections.singletonList("key"),
                        Collections.singletonList(new ReturnStmt(new StringLit(
                                map.containsKey(key) ? map.get(key).asStringOrNull() : "null"
                        ))))));
                mapObj.put("delete", Value.ofFunction(new FunctionDef("delete",
                        Collections.singletonList("key"),
                        Collections.singletonList(new ReturnStmt(new BoolLit(map.remove(key) != null))))));
                mapObj.put("has", Value.ofFunction(new FunctionDef("has",
                        Collections.singletonList("key"),
                        Collections.singletonList(new ReturnStmt(new BoolLit(map.containsKey(key)))))));
            } else {
                mapObj.put("set", Value.ofFunction(new FunctionDef("set",
                        Arrays.asList("key", "value"), Collections.emptyList())));
                mapObj.put("get", Value.ofFunction(new FunctionDef("get",
                        Collections.singletonList("key"), Collections.emptyList())));
                mapObj.put("delete", Value.ofFunction(new FunctionDef("delete",
                        Collections.singletonList("key"), Collections.emptyList())));
                mapObj.put("has", Value.ofFunction(new FunctionDef("has",
                        Collections.singletonList("key"), Collections.emptyList())));
            }

            mapObj.put("clear", Value.ofFunction(new FunctionDef("clear",
                    Collections.emptyList(),
                    Collections.singletonList(new ReturnStmt(new BoolLit(true))))));

            return Value.ofMap(mapObj);
        }

        private void step() {
            steps++;
            if (steps > maxSteps) {
                throw new ScriptRuntimeException("Превышен лимит шагов выполнения (" + maxSteps + ")");
            }
        }
    }
}