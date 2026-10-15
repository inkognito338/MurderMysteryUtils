package real.inkognito338.murdermysteryutils.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Лёгкий определитель языка без внешних библиотек.
 *
 * Как работает:
 *  1. По скрипту письма (иероглифы, кана, хангыль, тайский, иврит, ...) языки
 *     определяются сразу.
 *  2. Для кириллицы и латиницы каждому языку считаются очки:
 *       - за уникальные буквы (ў, ї, ß, ł, ...) — см. LETTER_RULES;
 *       - за слова из словаря частых слов (+WORD_WEIGHT за каждое слово).
 *  3. Побеждает язык с наибольшим счётом.
 *
 * "Обучение": словари можно дополнять файлом (см. {@link #reload(File)}),
 * формат строк:  код=слово слово слово
 */
public final class LanguageGuesser {

    private static final double WORD_WEIGHT = 2.0;

    /** Порядок важен: при равенстве очков побеждает язык, стоящий раньше. */
    private static final String[] LATIN = {
            "en", "de", "fr", "es", "pt", "it", "nl", "sv", "no", "da", "fi", "cs", "pl",
            "hu", "ro", "tr", "lt", "lv", "et", "vi", "id", "ms", "hr", "sl"
    };
    private static final String[] CYRILLIC = {"ru", "uk", "be", "bg", "sr", "kk"};

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}']+");

    // ── Правила по буквам ────────────────────────────────────────────────────

    private static final class Rule {
        final String chars;
        final double weight;

        Rule(String chars, double weight) {
            this.chars = chars;
            this.weight = weight;
        }
    }

    private static final Map<String, List<Rule>> LETTER_RULES = new HashMap<>();
    private static final Map<String, String> DEFAULT_WORDS = new LinkedHashMap<>();

    private static void rule(String lang, String chars, double weight) {
        List<Rule> list = LETTER_RULES.get(lang);
        if (list == null) {
            list = new ArrayList<>();
            LETTER_RULES.put(lang, list);
        }
        list.add(new Rule(chars, weight));
    }

    static {
        // ── Кириллица ────────────────────────────────────────────────────────
        // Положительный вес — буква характерна для языка, отрицательный — в нём не бывает.
        rule("ru", "ыэё", 1.0);
        rule("ru", "ъ", 0.5);
        rule("ru", "іїєґўәғқңөұүһђћљњџј", -5);

        rule("uk", "іїєґ", 3);
        rule("uk", "ыэёъ", -5);
        rule("uk", "ўәғқңөұүһђћљњџј", -5);

        rule("be", "ў", 5);
        rule("be", "і", 2);
        rule("be", "ыэё", 1);
        rule("be", "иъщ", -3);
        rule("be", "їєґәғқңөұүһђћљњџј", -5);

        rule("bg", "ъ", 1.5);
        rule("bg", "щ", 0.5);
        rule("bg", "ыэёіїєґўәғқңөұүһђћљњџј", -5);

        rule("sr", "ђћљњџј", 5);
        rule("sr", "ыэёіїєґў", -5);

        rule("kk", "әғқңөұүһ", 5);
        rule("kk", "і", 1.5);
        rule("kk", "ыэ", 0.5);

        // ── Латиница ─────────────────────────────────────────────────────────
        rule("de", "ß", 5);
        rule("de", "äöü", 1.5);

        rule("fr", "œ", 5);
        rule("fr", "çàâêîôûèù", 1);
        rule("fr", "é", 0.5);

        rule("es", "ñ¿¡", 5);
        rule("es", "áíóú", 0.7);
        rule("es", "é", 0.3);

        rule("pt", "ãõ", 5);
        rule("pt", "ç", 2);
        rule("pt", "êôàáíóú", 0.7);
        rule("pt", "é", 0.3);

        rule("it", "àèìòù", 1);
        rule("it", "é", 0.3);

        rule("nl", "ëï", 1);

        rule("sv", "å", 3);
        rule("sv", "äö", 1.5);

        rule("no", "æø", 3);
        rule("no", "å", 2);
        rule("da", "æø", 3);
        rule("da", "å", 2);

        rule("fi", "äö", 1.5);

        rule("cs", "ěřů", 5);
        rule("cs", "ďťň", 3);
        rule("cs", "čšž", 1.5);
        rule("cs", "áéíóúý", 0.5);

        rule("pl", "łąęśźżćń", 4);
        rule("pl", "ó", 1);

        rule("hu", "őű", 5);
        rule("hu", "áéíóöúü", 0.7);

        rule("ro", "ășțţ", 5);
        rule("ro", "âî", 3);

        rule("tr", "ığ", 5);
        rule("tr", "şç", 3);
        rule("tr", "öü", 1);

        rule("lt", "ąęėįųū", 4);
        rule("lt", "čšž", 1.5);

        rule("lv", "āēģīķļņū", 4);
        rule("lv", "čšž", 1.5);

        rule("et", "õ", 5);
        rule("et", "äöü", 1.5);
        rule("et", "šž", 1.5);

        rule("vi", "ăắằẳẵặấầẩẫậđơờớởỡợưừứửữựạảẹẻẽếềểễệỉịọỏốồổỗộụủỳỵỷỹ", 4);

        rule("hr", "đć", 4);
        rule("hr", "čšž", 1.5);
        rule("sl", "čšž", 1.5);

        // ── Словари частых слов (в нижнем регистре) ──────────────────────────
        DEFAULT_WORDS.put("en", "the be to of and in that have it for not on with he as you do at this but his by from they we say her she or an will my one all would there their what so up out if about who get which go me when make can like time no just him know take people into your good some could them see other than then now look only come its over think also back after use two how our work first well way even new want because any these give day most us is are was were am i'm i'll don't can't it's that's hello hi hey thanks thank please yes yeah lol ok okay bro dude nice cool wow sorry");
        DEFAULT_WORDS.put("de", "der die das und ist nicht ich du er sie es wir ihr ein eine zu mit auf für von den dem des im ja nein aber auch wie was wer wo warum wann bitte danke hallo tschüss gut schlecht sehr kann muss bin bist sind war habe hat haben noch schon nur dann wenn oder dass mir dir mich dich uns euch");
        DEFAULT_WORDS.put("fr", "le la les un une des et est ne pas je tu il elle nous vous ils elles de du en dans pour avec sur que qui oui non mais aussi comment quoi pourquoi quand où bonjour salut merci bonsoir très bien mal peut doit suis es sont était ai as ont déjà encore seulement alors si ou me te se moi toi lui c'est j'ai");
        DEFAULT_WORDS.put("es", "el la los las un una unos unas y es no yo tú él ella nosotros vosotros ellos de del en por para con sobre que quien sí pero también cómo qué cuándo dónde hola gracias adiós muy bien mal puede debe soy eres son era he has ha hemos han ya todavía solo entonces si o me te se mí ti lo le");
        DEFAULT_WORDS.put("pt", "o a os as um uma e é não eu tu ele ela nós vocês eles de do da em por para com sobre que quem sim mas também como quando onde olá obrigado obrigada tchau muito bem mal pode deve sou és são era tenho tem temos já ainda só então se ou me te mim ti");
        DEFAULT_WORDS.put("it", "il lo la i gli le un una e è non io tu lui lei noi voi loro di del in per con su che chi sì ma anche come cosa perché quando dove ciao grazie arrivederci molto bene male può deve sono sei siamo era ho hai ha abbiamo hanno già ancora solo allora se o mi ti ci");
        DEFAULT_WORDS.put("nl", "de het een en is niet ik jij je hij zij ze wij we jullie dat van in op voor met naar ja nee maar ook hoe wat wie waar waarom wanneer alsjeblieft bedankt hallo doei goed slecht heel kan moet ben bent zijn was heb hebt heeft hebben al nog alleen dan als of mij me jou");
        DEFAULT_WORDS.put("sv", "och att det som en är på jag du han hon vi ni de inte med för av till ja nej men också hur vad vem var varför när tack hej bra dålig mycket kan måste har hade finns redan bara sedan om eller mig dig");
        DEFAULT_WORDS.put("no", "og at det som en er på jeg du han hun vi dere de ikke med for av til ja nei men også hvordan hva hvem hvor hvorfor når takk hei bra dårlig veldig kan må har hadde finnes allerede bare så hvis eller meg deg");
        DEFAULT_WORDS.put("da", "og at det som en er på jeg du han hun vi i de ikke med for af til ja nej men også hvordan hvad hvem hvor hvorfor hvornår tak hej farvel god dårlig meget kan skal har havde findes allerede kun så hvis eller mig dig");
        DEFAULT_WORDS.put("fi", "ja on ei se että minä sinä hän me te he mutta myös miten mitä kuka missä miksi milloin kiitos hei moi hyvä huono erittäin voi täytyy olen olet ovat oli minulla jo vielä vain sitten jos tai minut sinut kyllä en et emme ette");
        DEFAULT_WORDS.put("cs", "a je to na se že já ty on ona my vy oni ne ano ale také jak co kdo kde proč kdy prosím děkuji ahoj nashledanou dobrý špatný velmi může musí jsem jsi jsou byl mám má už ještě jen potom jestli nebo mě tě");
        DEFAULT_WORDS.put("pl", "i w nie na się że ja ty on ona my wy oni tak ale też jak co kto gdzie dlaczego kiedy proszę dziękuję cześć dobry zły bardzo może musi jestem jesteś są był mam ma już jeszcze tylko potem jeśli albo mnie cię");
        DEFAULT_WORDS.put("hu", "és a az nem én te ő mi ti ők igen de is hogy mit ki hol miért mikor kérem köszönöm szia viszlát jó rossz nagyon tud kell vagyok vagy van volt már még csak aztán ha engem téged");
        DEFAULT_WORDS.put("ro", "și în nu pe eu tu el ea noi voi ei ele da dar cum ce cine unde când mulțumesc salut bun rău foarte poate trebuie sunt ești este era am ai are deja încă doar apoi dacă sau mine tine");
        DEFAULT_WORDS.put("tr", "ve bir bu da de ben sen o biz siz onlar evet hayır ama ayrıca nasıl ne kim nerede neden lütfen teşekkürler merhaba selam iyi kötü çok olabilir lazım benim senin var yok şimdi sadece sonra eğer veya beni seni");
        DEFAULT_WORDS.put("lt", "ir ne tai aš tu jis ji mes jūs jie taip bet kaip ką kas kur kodėl kada prašau ačiū labas sudie gerai blogai labai gali reikia esu esi yra buvo turiu jau dar tik tada jei arba mane tave");
        DEFAULT_WORDS.put("lv", "un nav tas es tu viņš viņa mēs jūs viņi jā bet arī kā ko kas kur kāpēc kad lūdzu paldies sveiki atā labi slikti ļoti var vajag esmu esi ir bija man jau vēl tikai tad ja vai mani tevi");
        DEFAULT_WORDS.put("et", "ja ei see mina sina tema meie teie nemad jah aga ka kuidas mis kes kus miks millal palun aitäh tere head hea halb väga saab peab olen oled on oli mul juba veel ainult siis kui või mind sind");
        DEFAULT_WORDS.put("vi", "và là không có tôi bạn anh chị chúng ta họ vâng dạ nhưng cũng như gì ai ở đâu tại sao khi nào xin vui lòng cảm ơn chào tạm biệt tốt xấu rất có thể phải đã còn chỉ sau nếu hoặc");
        DEFAULT_WORDS.put("id", "dan yang di ke dari ini itu saya kamu dia kami kita mereka ya tidak bukan tapi juga bagaimana apa siapa dimana kenapa kapan tolong terima kasih halo hai selamat baik buruk sangat bisa harus adalah sudah masih hanya lalu jika atau aku");
        DEFAULT_WORDS.put("ms", "dan yang di ke dari ini itu saya awak dia kami kita mereka ya tidak bukan tetapi juga bagaimana apa siapa mana kenapa bila tolong terima kasih helo hai selamat baik buruk sangat boleh mesti adalah sudah masih hanya kemudian jika atau aku");
        DEFAULT_WORDS.put("hr", "i u ne na se da ja ti on ona mi vi oni ali također kako što tko gdje zašto kada molim hvala bok doviđenja dobar loš vrlo može treba sam si je smo su bio imam već još samo onda ako ili mene tebe");
        DEFAULT_WORDS.put("sl", "in je ne na se da jaz ti on ona mi vi oni ampak tudi kako kaj kdo kje zakaj kdaj prosim hvala živjo nasvidenje dober slab zelo lahko mora sem si smo so bil imam že še samo potem če ali mene tebe");

        DEFAULT_WORDS.put("ru", "и в не на я что это как ты он она мы они вы да нет но так все если когда чтобы тебе мне есть был была было просто привет спасибо пока ещё уже тут там где кто почему зачем чем очень можно нужно надо давай ладно хорошо плохо блин ну или для при из от по за до со без под над меня тебя его её их нас вас");
        DEFAULT_WORDS.put("uk", "і в не на я що це як ти він вона ми вони ви так ні але також коли щоб тобі мені є був була було просто привіт дякую бувай вже тут там де хто чому навіщо дуже можна треба давай гаразд добре погано ну або для від по за до без мене тебе його її їх нас вас");
        DEFAULT_WORDS.put("be", "і у не на я што гэта як ты ён яна мы яны вы так але таксама калі каб табе мне ёсць быў была было проста прывітанне дзякуй бывай ужо тут там дзе хто чаму вельмі можна трэба давай добра дрэнна ну або для ад па за да без мяне цябе яго яе іх нас вас");
        DEFAULT_WORDS.put("bg", "и в не на аз че това как ти той тя ние те вие да но също ако когато за теб мен има беше просто здравей здрасти благодаря чао вече тук там къде кой защо много може трябва хайде добре лошо ами или от по до без него нея тях нас вас моля е съм си са ще ли се");
        DEFAULT_WORDS.put("sr", "и у не на ја да је се ти он она ми они ви али такође када теби мени има био била било само здраво хвала ћао већ овде тамо где ко зашто врло може треба хајде добро лоше па или за од по до без мене тебе њега њу њих нас вас молим шта како ово сам си смо су");
        DEFAULT_WORDS.put("kk", "және бұл мен сен ол біз сіз олар иә жоқ бірақ да де егер қашан үшін саған маған бар болды жай сәлем рахмет сау бол қазір мұнда онда қайда кім неге өте мүмкін керек жақсы жаман немесе қалай не");
    }

    // ── Состояние ────────────────────────────────────────────────────────────

    private volatile Map<String, Set<String>> words = buildWords(null);

    private static Map<String, Set<String>> buildWords(Map<String, String> extra) {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, String> e : DEFAULT_WORDS.entrySet()) {
            result.put(e.getKey(), toSet(e.getValue()));
        }
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                Set<String> set = result.get(e.getKey());
                if (set == null) {
                    set = new HashSet<>();
                    result.put(e.getKey(), set);
                }
                set.addAll(toSet(e.getValue()));
            }
        }
        return result;
    }

    private static Set<String> toSet(String line) {
        Set<String> set = new HashSet<>();
        for (String w : line.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!w.isEmpty()) set.add(w);
        }
        return set;
    }

    /**
     * Пересобирает словари: встроенные + слова из файла.
     * Файл создаётся с шаблоном, если его ещё нет.
     * Формат строки:  код=слово слово слово   (строки с # игнорируются)
     */
    public void reload(File file) {
        Map<String, String> extra = new HashMap<>();
        try {
            if (file != null) {
                if (!file.exists()) writeTemplate(file);
                try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq <= 0) continue;
                        String code = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                        String list = line.substring(eq + 1);
                        String prev = extra.get(code);
                        extra.put(code, prev == null ? list : prev + " " + list);
                    }
                }
            }
        } catch (IOException ignored) {
            // нет файла или нет доступа — работаем только со встроенными словарями
        }
        words = buildWords(extra);
    }

    private static void writeTemplate(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        String template =
                "# Дополнительные слова для определения языка ChatTranslator.\n"
                        + "# Формат:  код=слово слово слово\n"
                        + "# Коды: ru uk be bg sr kk en de fr es pt it nl sv no da fi cs pl hu ro tr lt lv et vi id ms hr sl\n"
                        + "# Пример (раскомментируйте):\n"
                        + "# ru=короче вообще типа чел\n"
                        + "# en=gg ez noob\n";
        Files.write(file.toPath(), template.getBytes(StandardCharsets.UTF_8));
    }

    // ── Определение ──────────────────────────────────────────────────────────

    /**
     * @return код языка ("ru", "en", "cn", "ja", ...) или null, если определить не удалось.
     */
    public String guess(String text) {
        if (text == null) return null;
        String s = text.toLowerCase(Locale.ROOT);

        int latin = 0, cyr = 0, han = 0, kana = 0, hangul = 0;
        int thai = 0, hebrew = 0, deva = 0, greek = 0, arabic = 0;

        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) continue;

            switch (Character.UnicodeScript.of(cp)) {
                case LATIN:      latin++;  break;
                case CYRILLIC:   cyr++;    break;
                case HAN:        han++;    break;
                case HIRAGANA:
                case KATAKANA:   kana++;   break;
                case HANGUL:     hangul++; break;
                case THAI:       thai++;   break;
                case HEBREW:     hebrew++; break;
                case DEVANAGARI: deva++;   break;
                case GREEK:      greek++;  break;
                case ARABIC:     arabic++; break;
                default: break;
            }
        }

        // Японский всегда содержит кану; чисто иероглифический текст считаем китайским
        if (kana > 0) return "ja";

        int max = Math.max(Math.max(Math.max(latin, cyr), Math.max(han, hangul)),
                Math.max(Math.max(thai, hebrew), Math.max(Math.max(deva, greek), arabic)));
        if (max == 0) return null;

        if (cyr == max)    return guessCyrillic(s);
        if (latin == max)  return guessLatin(s);
        if (han == max)    return "cn";
        if (hangul == max) return "ko";
        if (thai == max)   return "th";
        if (hebrew == max) return "he";
        if (deva == max)   return "hi";
        if (greek == max)  return "el";
        return guessArabicScript(s);
    }

    private String guessArabicScript(String s) {
        // Урду: ٹ ڈ ڑ ں ے ھ
        if (containsAny(s, "\u0679\u0688\u0691\u06BA\u06D2\u06BE")) return "ur";
        // Фарси: پ چ ژ گ, а также персидские ک и ی
        if (containsAny(s, "\u067E\u0686\u0698\u06AF\u06A9\u06CC")) return "fa";
        return "ar";
    }

    private String guessCyrillic(String s) {
        List<String> tokens = tokens(s);
        String best = "ru";
        double bestScore = Double.NEGATIVE_INFINITY;

        for (String lang : CYRILLIC) {
            double score = score(lang, s, tokens);
            if ("ru".equals(lang)) score += 0.3; // при полном равенстве — русский
            if (score > bestScore) {
                bestScore = score;
                best = lang;
            }
        }
        return best;
    }

    private String guessLatin(String s) {
        List<String> tokens = tokens(s);
        String best = null;
        double bestScore = 0;

        for (String lang : LATIN) {
            double score = score(lang, s, tokens);
            if (score > bestScore) {
                bestScore = score;
                best = lang;
            }
        }
        if (best != null) return best;

        // Нет ни уникальных букв, ни известных слов. Чистый ASCII в чате — почти всегда
        // английский; с диакритикой, которую мы не знаем, лучше не гадать.
        return isAscii(s) ? "en" : null;
    }

    private double score(String lang, String s, List<String> tokens) {
        double score = 0;

        List<Rule> rules = LETTER_RULES.get(lang);
        if (rules != null) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                for (Rule r : rules) {
                    if (r.chars.indexOf(c) >= 0) score += r.weight;
                }
            }
        }

        Set<String> dict = words.get(lang);
        if (dict != null) {
            for (String t : tokens) {
                if (dict.contains(t)) score += WORD_WEIGHT;
            }
        }
        return score;
    }

    private static List<String> tokens(String s) {
        List<String> result = new ArrayList<>();
        for (String raw : TOKEN_SPLIT.split(s)) {
            String t = raw;
            while (t.startsWith("'")) t = t.substring(1);
            while (t.endsWith("'")) t = t.substring(0, t.length() - 1);
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }

    private static boolean containsAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return true;
        }
        return false;
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127 && Character.isLetter(c)) return false;
        }
        return true;
    }
}