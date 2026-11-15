import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.legoshi.parkourcalc.core.anglesolver.ConstraintDeriver;
import de.legoshi.parkourcalc.core.sim.AABB;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class DeriveSelectedFootprints {
    public static void main(String[] args) throws Exception {
        Gson gson = new Gson();
        JsonObject request = gson.fromJson(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8), JsonObject.class);
        JsonArray blocks = request.getAsJsonArray("blocks");
        List<AABB> obstacles = new ArrayList<>();
        for (JsonElement value : blocks) {
            JsonArray b = value.getAsJsonObject().getAsJsonArray("box");
            obstacles.add(new AABB(new Vec3dCore(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble()),
                    new Vec3dCore(b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble())));
        }
        JsonArray derived = new JsonArray();
        for (int i = 0; i < blocks.size(); i++) {
            JsonObject block = blocks.get(i).getAsJsonObject();
            JsonArray ref = block.getAsJsonArray("reference");
            JsonObject record = new JsonObject();
            record.add("id", block.get("id"));
            record.add("box", block.get("box"));
            record.add("reference", ref);
            record.addProperty("topY", obstacles.get(i).max.y);
            JsonArray variants = new JsonArray();
            for (JsonElement yaw : request.getAsJsonArray("facings")) {
                double[] footprint = ConstraintDeriver.deriveFootprint(obstacles.get(i), ref.get(0).getAsDouble(),
                        ref.get(1).getAsDouble(), obstacles, false, yaw.getAsFloat());
                JsonObject variant = new JsonObject();
                variant.add("yaw", yaw);
                variant.add("centerBounds", gson.toJsonTree(footprint));
                variants.add(variant);
            }
            record.add("variants", variants);
            derived.add(record);
        }
        JsonObject result = new JsonObject();
        result.addProperty("model", "ConstraintDeriver.deriveFootprint with neighboring obstacles, legacy collision");
        result.addProperty("halfWidth", ConstraintDeriver.HALF);
        result.addProperty("bodyHeight", ConstraintDeriver.BODY_HEIGHT);
        result.add("blocks", derived);
        System.out.println(gson.toJson(result));
    }
}
