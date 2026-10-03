package org.offlineatlas;
import java.io.File;
import java.io.IOException;

/** Crash-recoverable replacement; validation happens before this transaction. */
final class AssetSwap {
    static void recover(File target) throws IOException {
        File backup=new File(target.getPath()+".backup");
        if(!target.exists() && backup.exists() && !backup.renameTo(target))
            throw new IOException("Cannot recover the last installed asset");
    }
    static void install(File incoming,File target) throws IOException {
        File backup=new File(target.getPath()+".backup");
        if(backup.exists() && !backup.delete()) throw new IOException("Cannot clear previous asset backup");
        boolean old=target.exists();
        if(old && !target.renameTo(backup)) throw new IOException("Cannot preserve the installed asset");
        if(!incoming.renameTo(target)) {
            if(old && !backup.renameTo(target)) throw new IOException("Installation failed; previous asset remains in "+backup.getName());
            throw new IOException("Cannot install incoming asset; previous asset preserved");
        }
        backup.delete();
    }
    private AssetSwap() { }
}
