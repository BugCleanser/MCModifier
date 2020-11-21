package top.dsbbs2.mm;

public class Main {
	public static void main(String[] args) {
		java.util.logging.Logger.getLogger(Main.class.getName()).info("MinecraftModifier loaded!");
		net.minecraft.server.Main.main(args);
	}
}
