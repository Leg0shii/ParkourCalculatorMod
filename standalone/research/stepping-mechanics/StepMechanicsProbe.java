import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.core.replay.StaticScene;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

public class StepMechanicsProbe {
    static void run(String name, String solids, double y, double dx, double dy, boolean ground) {
        run(name, solids, y, dx, dy, 0, ground);
    }
    static void run(String name, String solids, double y, double dx, double dy, double dz, boolean ground) {
        StaticScene s = new StaticScene(new JsonParser().parse("{\"version\":1,\"profile\":\"minecraft-1.8.9\",\"bounds\":[-10,-5,-10,10,10,10],\"solids\":[" + solids + "]}").getAsJsonObject());
        StaticScene.Contact c = s.move(s.startBox(new Vec3dCore(.5,y,.5)),new Vec3dCore(dx,dy,dz),ground,false);
        System.out.printf("%s: y=%.17g -> %.17g netY=%.17g rawY=%.17g x=%.17g z=%.17g stepped=%s grounded=%s supported=%s vertical=%s carryY=%.17g\n",name,y,c.position.y,c.position.y-y,dy,c.position.x,c.position.z,c.stepped,c.onGround,s.supported(c.box),c.verticalCollision,c.velocity.y);
    }
    public static void main(String[] args) {
        String wide = "[-5,0,-5,5,1,5],[1,1,-1,2,1.5,2]";
        String corner = "[-1,0,-1,1,1,1],[0.85,1,0,1.15,1.5,0.55]";
        run("normal_step",wide,1,.8,-.0784000015258789,true);
        run("slab_boost",wide,1,.8,(double).42F,true);
        run("blip",wide,1.2,.8,-.4,false);
        run("corner_blip_up",corner,1.2,.45,-.4,.36,false);
        run("corner_jump_cancel",corner,1,.45,(double).42F,.36,true);
        run("blip_up",wide,1.6,.8,-.8,false);
        run("grind","[-5,0,-5,5,1,5],[1,2.9,-1,3,4,2]",1,.8,(double).42F,true);
        String zCorner = "[-1,0,-1,1,1,1],[0,1,0.85,0.55,1.5,1.15]";
        run("z_facing_normal_jump",zCorner,1,.36,(double).42F,.45,true);
        run("z_facing_lower_landing",zCorner,1.2,.36,-.4,.45,false);
        run("return_leg_equality",wide,1.25,.8,-.3500000238418579,false);
    }
}
