import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class DeobfConv {

    public static void main(String[] args) throws Exception {
        Path proguard = Paths.get(args[0]);
        Path outTiny = Paths.get(args[1]);
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(proguard, MappingFormat.PROGUARD_FILE, tree);
        MemoryMappingTree switched = new MemoryMappingTree();
        tree.accept(new MappingSourceNsSwitch(switched, tree.getDstNamespaces().get(0)));
        try (MappingWriter writer = MappingWriter.create(outTiny, MappingFormat.TINY_2_FILE)) {
            switched.accept(writer);
        }
    }
}
