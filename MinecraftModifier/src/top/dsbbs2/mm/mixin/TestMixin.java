package top.dsbbs2.mm.mixin;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.dedicated.DedicatedServer;

@Mixin(DedicatedServer.class)
public class TestMixin {
	@Inject(at=@At(value="HEAD"), method = "initServer",cancellable=true)
	public void initServer(CallbackInfoReturnable<Boolean> cbi) throws IOException {
		LogManager.getLogger().fatal("pwqwpwqwpwq");
	}
}
