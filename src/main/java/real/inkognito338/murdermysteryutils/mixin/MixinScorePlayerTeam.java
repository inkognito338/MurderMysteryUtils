
//package real.inkognito338.murdermysteryutils.mixin;
//
//import net.minecraft.scoreboard.ScorePlayerTeam;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.ModifyArg;
//import real.inkognito338.murdermysteryutils.modules.NameProtect;
//
//@Mixin(ScorePlayerTeam.class)
//public abstract class MixinScorePlayerTeam {
//
//    @ModifyArg(
//            method = "setPrefix",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Ljava/lang/String;replaceAll(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
//                    ordinal = 0
//            ),
//            index = 0
//    )
//    private String replacePrefix(String prefix) {
//        NameProtect module = NameProtect.getInstance();
//        if (module != null && module.isToggled() && prefix != null) {
//            String real = NameProtect.getRealName();
//            if (!real.isEmpty() && prefix.contains(real)) {
//                String replaced = prefix.replace(real, NameProtect.getFakeName());
//                System.out.println("[NameProtect] Prefix replaced: " + prefix + " -> " + replaced);
//                return replaced;
//            }
//        }
//        return prefix;
//    }
//
//    @ModifyArg(
//            method = "setSuffix",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Ljava/lang/String;replaceAll(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
//                    ordinal = 0
//            ),
//            index = 0
//    )
//    private String replaceSuffix(String suffix) {
//        NameProtect module = NameProtect.getInstance();
//        if (module != null && module.isToggled() && suffix != null) {
//            String real = NameProtect.getRealName();
//            if (!real.isEmpty() && suffix.contains(real)) {
//                String replaced = suffix.replace(real, NameProtect.getFakeName());
//                System.out.println("[NameProtect] Suffix replaced: " + suffix + " -> " + replaced);
//                return replaced;
//            }
//        }
//        return suffix;
//    }
//
//    @ModifyArg(
//            method = "getPrefix",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Ljava/lang/String;replaceAll(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
//                    ordinal = 0
//            ),
//            index = 0
//    )
//    private String replaceGetPrefix(String prefix) {
//        NameProtect module = NameProtect.getInstance();
//        if (module != null && module.isToggled() && prefix != null) {
//            String real = NameProtect.getRealName();
//            if (!real.isEmpty() && prefix.contains(real)) {
//                return prefix.replace(real, NameProtect.getFakeName());
//            }
//        }
//        return prefix;
//    }
//
//    @ModifyArg(
//            method = "getSuffix",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Ljava/lang/String;replaceAll(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
//                    ordinal = 0
//            ),
//            index = 0
//    )
//    private String replaceGetSuffix(String suffix) {
//        NameProtect module = NameProtect.getInstance();
//        if (module != null && module.isToggled() && suffix != null) {
//            String real = NameProtect.getRealName();
//            if (!real.isEmpty() && suffix.contains(real)) {
//                return suffix.replace(real, NameProtect.getFakeName());
//            }
//        }
//        return suffix;
//    }
//}