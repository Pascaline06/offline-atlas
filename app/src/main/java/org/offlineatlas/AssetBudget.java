package org.offlineatlas;
import java.io.File;
import java.io.IOException;

/** Reserve 1 GB of the 50 GB asset ceiling for APK, libraries, caches, and logs. */
final class AssetBudget {
    static final long PRIVATE_LIMIT=49_000_000_000L;
    static final long FREE_RESERVE=256_000_000L;
    static long bytes(File file) {
        if(!file.exists()) return 0;
        if(file.isFile()) return file.length();
        File[] children=file.listFiles();
        long size=0;
        if(children!=null) for(File child:children) size+=bytes(child);
        return size;
    }
    static void requireSpace(File privateRoot,long incoming) throws IOException {
        if(incoming<0 || bytes(privateRoot)>PRIVATE_LIMIT-incoming)
            throw new IOException("Installation would exceed the offline asset storage budget");
        if(privateRoot.getUsableSpace()<incoming+FREE_RESERVE)
            throw new IOException("Not enough free storage for a safe installation; keep at least 256 MB free");
    }
    private AssetBudget() { }
}
