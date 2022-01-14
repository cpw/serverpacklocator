//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package cpw.mods.forge.serverpackutility;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import cpw.mods.modlauncher.api.TypesafeMap.Key;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenEvent.DrawScreenEvent.Pre;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment.Keys;

@Mod("serverpacklocatorutility")
public class UtilityMod {
    public UtilityMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClient);
    }

    private void onClient(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(UtilityMod.Wrapper::onShowGui);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class Wrapper {
        private static boolean brandingHacked = false;
        private static final Supplier<String> statusMessage;
        private static final Field brandingList;

        private Wrapper() {
        }

        @SuppressWarnings("rawtypes")
        static void onShowGui(Pre event) {
            if (!brandingHacked) {
                if (event.getScreen() instanceof TitleScreen) {
                    List<String> branding = (List<String>) LamdbaExceptionUtils.uncheck(() -> (List)brandingList.get(null));
                    if (branding != null) {
                        Builder<String> brd = ImmutableList.builder();
                        brd.addAll(branding);
                        brd.add(statusMessage.get());
                        LamdbaExceptionUtils.uncheck(() -> brandingList.set(null, brd.build()));
                        brandingHacked = true;
                    }

                }
            }
        }

        static {
            Class<?> brdControl = LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.internal.BrandingControl", true, Thread.currentThread().getContextClassLoader()));
            brandingList = LamdbaExceptionUtils.uncheck(() -> brdControl.getDeclaredField("overCopyrightBrandings"));
            brandingList.setAccessible(true);

            Supplier statMessage;
            try {
                Optional<ClassLoader> classLoader = Launcher.INSTANCE.environment().getProperty((Key)Keys.LOCATORCLASSLOADER.get());
                Class<?> clz = LamdbaExceptionUtils.uncheck(() -> Class.forName("cpw.mods.forge.serverpacklocator.ModAccessor", true, classLoader.orElse(Thread.currentThread().getContextClassLoader())));
                Method status = LamdbaExceptionUtils.uncheck(() -> clz.getMethod("status"));
                statMessage = LamdbaExceptionUtils.uncheck(() -> (Supplier)status.invoke(null));
            } catch (Throwable var5) {
                statMessage = () -> "ServerPack: FAILED TO LOAD";
            }

            statusMessage = statMessage;
        }
    }
}
