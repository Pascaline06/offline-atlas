package org.offlineatlas;
import java.nio.file.*;
import java.io.*;
public final class AssetCases {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("atlas-assets");
        try {
            File target=root.resolve("model").toFile(), incoming=root.resolve("incoming").toFile();
            Files.writeString(target.toPath(),"old usable data");
            try {AssetSwap.install(incoming,target);throw new AssertionError("Missing incoming accepted");}
            catch(IOException expected) {if(!Files.readString(target.toPath()).equals("old usable data")) throw new AssertionError("Old asset lost");}
            Files.writeString(incoming.toPath(),"new validated data");
            AssetSwap.install(incoming,target);
            if(!Files.readString(target.toPath()).equals("new validated data")) throw new AssertionError("Replacement failed");
            File backup=new File(target.getPath()+".backup");
            target.renameTo(backup);AssetSwap.recover(target);
            if(!target.exists()) throw new AssertionError("Crash recovery failed");
            try {AssetBudget.requireSpace(root.toFile(),AssetBudget.PRIVATE_LIMIT);throw new AssertionError("Budget exceeded");}
            catch(IOException expected) { }
        } finally {try(var files=Files.walk(root)) {files.sorted(java.util.Comparator.reverseOrder()).forEach(path->{try {Files.delete(path);}catch(IOException e){throw new RuntimeException(e);}});}}
        System.out.println("Atomic asset and budget cases passed");
    }
}
