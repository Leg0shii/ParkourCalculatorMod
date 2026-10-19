package de.legoshi.parkourcalc.gpu;

import de.legoshi.parkourcalc.core.anglesolver.noturn.PoolScreenAccelerator;
import de.legoshi.parkourcalc.core.anglesolver.noturn.PoolScreenTables;
import de.legoshi.parkourcalc.core.anglesolver.noturn.SchedulePoolScreen;
import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_program;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class OpenClPoolScreen implements SchedulePoolScreen {

    public static final class Factory implements PoolScreenAccelerator.Factory {
        @Override
        public SchedulePoolScreen open(PoolScreenTables tables) {
            return new OpenClPoolScreen(tables);
        }

        @Override
        public String describe() {
            OpenClContext c = context;
            return c != null ? c.deviceName() + " (OpenCL)" : "OpenCL, starts with the next search";
        }

        @Override
        public String toString() {
            return OpenClPoolScreen.stats();
        }
    }

    private static final Object LOCK = new Object();
    private static final int DISK_CHUNK = Integer.getInteger("pkc.gpu.diskChunk", 262144);
    private static final int SCREEN_CHUNK = Integer.getInteger("pkc.gpu.screenChunk", 16384);
    private static final int UNIT_STEPS = 12;
    private static final int UNIT_REFINE = 4;
    private static final int UNIT_LEVELS_FINE = 4;
    private static OpenClContext context;
    private static final Map<String, cl_program> PROGRAMS = new HashMap<>();
    private static String template;
    private static final AtomicLong batches = new AtomicLong();
    private static final AtomicLong schedules = new AtomicLong();
    private static final AtomicLong screened = new AtomicLong();
    private static final AtomicLong diskNanos = new AtomicLong();
    private static final AtomicLong screenNanos = new AtomicLong();
    private static final AtomicLong hostNanos = new AtomicLong();

    private final PoolScreenTables t;
    private final cl_kernel diskKernel;
    private final cl_kernel screenKernel;
    private final cl_mem tabBuf;
    private final cl_mem coefBuf;
    private final cl_mem shiftBuf;
    private final cl_mem axisBuf;
    private final cl_mem kindBuf;
    private final cl_mem groupBuf;
    private final cl_mem objBuf;
    private final cl_mem paramBuf;
    private final cl_mem cosBuf;
    private final cl_mem sinBuf;
    private int capacity;
    private cl_mem combosBuf;
    private cl_mem diskViolBuf;
    private cl_mem diskThetaBuf;
    private cl_mem idxBuf;
    private cl_mem sViolBuf;
    private cl_mem sThetaBuf;
    private cl_mem sObjBuf;
    private float[] diskViol;
    private float[] diskTheta;
    private int[] idx;
    private float[] sViol;
    private float[] sTheta;
    private float[] sObj;
    private boolean closed;

    public static String stats() {
        return String.format(Locale.ROOT, "gpu batches=%d schedules=%d screened=%d disk=%.2fs screen=%.2fs host=%.2fs",
                batches.get(), schedules.get(), screened.get(), diskNanos.get() / 1e9, screenNanos.get() / 1e9,
                hostNanos.get() / 1e9);
    }

    private OpenClPoolScreen(PoolScreenTables tables) {
        this.t = tables;
        synchronized (LOCK) {
            if (context == null) context = new OpenClContext();
            cl_program program = PROGRAMS.get(tables.programKey());
            if (program == null) {
                program = context.buildProgram(source(tables), "-cl-fast-relaxed-math");
                PROGRAMS.put(tables.programKey(), program);
            }
            diskKernel = CL.clCreateKernel(program, "disk", null);
            screenKernel = CL.clCreateKernel(program, "screen", null);
            tabBuf = floats(tables.tab);
            coefBuf = floats(tables.coefW);
            shiftBuf = floats(tables.shift0);
            axisBuf = ints(tables.wallAxisX);
            kindBuf = ints(tables.wallKind);
            groupBuf = ints(tables.groupOf);
            objBuf = floats(tables.objCoef);
            paramBuf = floats(new float[]{tables.loX, tables.hiX, tables.loZ, tables.hiZ, tables.objConst,
                    tables.objAxisX ? 1.0f : 0.0f, tables.maximize ? 1.0f : 0.0f, tables.diskEarlyOut, tables.diskKeep});
            float[] cg = new float[tables.diskGrid];
            float[] sg = new float[tables.diskGrid];
            for (int g = 0; g < tables.diskGrid; g++) {
                double th = -Math.PI + g * (2.0 * Math.PI / tables.diskGrid);
                cg[g] = (float) Math.cos(th);
                sg[g] = (float) Math.sin(th);
            }
            cosBuf = floats(cg);
            sinBuf = floats(sg);
        }
    }

    private static cl_mem floats(float[] data) {
        return CL.clCreateBuffer(context.context(), CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_float * Math.max(1, data.length), Pointer.to(data), null);
    }

    private static cl_mem ints(int[] data) {
        return CL.clCreateBuffer(context.context(), CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_int * Math.max(1, data.length), Pointer.to(data), null);
    }

    private static String loadTemplate() {
        if (template != null) return template;
        try (InputStream in = OpenClPoolScreen.class.getResourceAsStream("pool_screen.cl")) {
            if (in == null) throw new IllegalStateException("pool_screen.cl missing");
            byte[] buf = new byte[1 << 16];
            StringBuilder sb = new StringBuilder();
            int r;
            while ((r = in.read(buf)) > 0) sb.append(new String(buf, 0, r, StandardCharsets.UTF_8));
            template = sb.toString();
            return template;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String source(PoolScreenTables t) {
        StringBuilder sb = new StringBuilder();
        sb.append("#define N ").append(t.n).append('\n');
        sb.append("#define W ").append(t.walls).append('\n');
        sb.append("#define G ").append(t.groups).append('\n');
        sb.append("#define MAINGROUP ").append(t.mainGroup).append('\n');
        sb.append("#define FREE ").append(t.freeGroups.length).append('\n');
        sb.append("#define FREE_G0 ").append(t.freeGroups.length > 0 ? t.freeGroups[0] : -1).append('\n');
        sb.append("#define FREE_G1 ").append(t.freeGroups.length > 1 ? t.freeGroups[1] : -1).append('\n');
        sb.append("#define OBJ_AXIS_X ").append(t.objAxisX ? 1 : 0).append('\n');
        sb.append("#define MAXIMIZE ").append(t.maximize ? 1 : 0).append('\n');
        sb.append("#define DISKGRID ").append(t.diskGrid).append('\n');
        sb.append("#define CSTRIDE ").append(Math.max(1, t.diskCoarseStride)).append('\n');
        sb.append("#define MSTRIDE ").append(Math.max(1, t.diskMidStride)).append('\n');
        sb.append("__constant int FREE_LIST[").append(Math.max(1, t.freeGroups.length)).append("] = {");
        if (t.freeGroups.length == 0) sb.append("-1");
        for (int i = 0; i < t.freeGroups.length; i++) sb.append(i > 0 ? "," : "").append(t.freeGroups[i]);
        sb.append("};\n");
        float[] sc = new float[UNIT_STEPS];
        float[] ss = new float[UNIT_STEPS];
        for (int k = 0; k < UNIT_STEPS; k++) {
            double psi = -Math.PI + k * (2.0 * Math.PI / UNIT_STEPS);
            sc[k] = (float) Math.cos(psi);
            ss[k] = (float) Math.sin(psi);
        }
        constant(sb, "STEP_COS", sc);
        constant(sb, "STEP_SIN", ss);
        int per = 2 * UNIT_REFINE + 1;
        float[] rc = new float[UNIT_LEVELS_FINE * per];
        float[] rs = new float[UNIT_LEVELS_FINE * per];
        double rstep = (2.0 * Math.PI / UNIT_STEPS) / (UNIT_REFINE + 1);
        for (int level = 0; level < UNIT_LEVELS_FINE; level++) {
            for (int j = -UNIT_REFINE; j <= UNIT_REFINE; j++) {
                rc[level * per + j + UNIT_REFINE] = (float) Math.cos(j * rstep);
                rs[level * per + j + UNIT_REFINE] = (float) Math.sin(j * rstep);
            }
            rstep /= (UNIT_REFINE + 1);
        }
        constant(sb, "REFINE_COS", rc);
        constant(sb, "REFINE_SIN", rs);
        constant(sb, "RESTART_OFF", new float[]{0.0f, (float) (0.5 * Math.PI), (float) (-0.5 * Math.PI), (float) Math.PI});
        sb.append(loadTemplate());
        return sb.toString();
    }

    private static void constant(StringBuilder sb, String name, float[] values) {
        sb.append("__constant float ").append(name).append('[').append(values.length).append("] = {");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.9ef", values[i]));
        }
        sb.append("};\n");
    }

    private void ensureCapacity(int count) {
        if (count <= capacity) return;
        releaseBatchBuffers();
        capacity = count;
        long items = (long) count * VARIANTS;
        combosBuf = CL.clCreateBuffer(context.context(), CL.CL_MEM_READ_ONLY, (long) count * t.n, null, null);
        diskViolBuf = CL.clCreateBuffer(context.context(), CL.CL_MEM_WRITE_ONLY, items * Sizeof.cl_float, null, null);
        diskThetaBuf = CL.clCreateBuffer(context.context(), CL.CL_MEM_WRITE_ONLY, items * Sizeof.cl_float, null, null);
        idxBuf = CL.clCreateBuffer(context.context(), CL.CL_MEM_READ_ONLY, items * Sizeof.cl_int, null, null);
        sViolBuf = CL.clCreateBuffer(context.context(), CL.CL_MEM_WRITE_ONLY, items * Sizeof.cl_float, null, null);
        sThetaBuf = CL.clCreateBuffer(context.context(), CL.CL_MEM_WRITE_ONLY, items * Sizeof.cl_float, null, null);
        sObjBuf = CL.clCreateBuffer(context.context(), CL.CL_MEM_WRITE_ONLY, items * Sizeof.cl_float, null, null);
        diskViol = new float[(int) items];
        diskTheta = new float[(int) items];
        idx = new int[(int) items];
        sViol = new float[(int) items];
        sTheta = new float[(int) items];
        sObj = new float[(int) items];
    }

    private void releaseBatchBuffers() {
        cl_mem[] bufs = {combosBuf, diskViolBuf, diskThetaBuf, idxBuf, sViolBuf, sThetaBuf, sObjBuf};
        for (cl_mem b : bufs) if (b != null) CL.clReleaseMemObject(b);
        combosBuf = diskViolBuf = diskThetaBuf = idxBuf = sViolBuf = sThetaBuf = sObjBuf = null;
        capacity = 0;
    }

    @Override
    public float[] evaluate(byte[] combos, int count) {
        if (closed) throw new IllegalStateException("closed");
        float[] out = new float[count * VARIANTS * FLOATS_PER_VARIANT];
        synchronized (LOCK) {
            long t0 = System.nanoTime();
            ensureCapacity(count);
            int items = count * VARIANTS;
            CL.clEnqueueWriteBuffer(context.queue(), combosBuf, CL.CL_TRUE, 0, (long) count * t.n, Pointer.to(combos), 0, null, null);
            int a = 0;
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_int, Pointer.to(new int[]{count}));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(combosBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(tabBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(coefBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(shiftBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(axisBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(kindBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(groupBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(paramBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(cosBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(sinBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(diskViolBuf));
            CL.clSetKernelArg(diskKernel, a++, Sizeof.cl_mem, Pointer.to(diskThetaBuf));
            enqueueChunked(diskKernel, items, DISK_CHUNK);
            CL.clEnqueueReadBuffer(context.queue(), diskViolBuf, CL.CL_TRUE, 0, (long) items * Sizeof.cl_float, Pointer.to(diskViol), 0, null, null);
            CL.clEnqueueReadBuffer(context.queue(), diskThetaBuf, CL.CL_TRUE, 0, (long) items * Sizeof.cl_float, Pointer.to(diskTheta), 0, null, null);
            long t1 = System.nanoTime();
            int m = 0;
            float keep = t.diskKeep;
            for (int gid = 0; gid < items; gid++) {
                if (diskViol[gid] <= keep) idx[m++] = gid;
            }
            if (m > 0) {
                CL.clEnqueueWriteBuffer(context.queue(), idxBuf, CL.CL_TRUE, 0, (long) m * Sizeof.cl_int, Pointer.to(idx), 0, null, null);
                a = 0;
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_int, Pointer.to(new int[]{m}));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(idxBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(combosBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(tabBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(coefBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(shiftBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(axisBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(kindBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(groupBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(objBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(paramBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(diskThetaBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(sViolBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(sThetaBuf));
                CL.clSetKernelArg(screenKernel, a++, Sizeof.cl_mem, Pointer.to(sObjBuf));
                enqueueChunked(screenKernel, m, SCREEN_CHUNK);
                CL.clEnqueueReadBuffer(context.queue(), sViolBuf, CL.CL_TRUE, 0, (long) m * Sizeof.cl_float, Pointer.to(sViol), 0, null, null);
                CL.clEnqueueReadBuffer(context.queue(), sThetaBuf, CL.CL_TRUE, 0, (long) m * Sizeof.cl_float, Pointer.to(sTheta), 0, null, null);
                CL.clEnqueueReadBuffer(context.queue(), sObjBuf, CL.CL_TRUE, 0, (long) m * Sizeof.cl_float, Pointer.to(sObj), 0, null, null);
            }
            long t2 = System.nanoTime();
            for (int gid = 0; gid < items; gid++) {
                int o = gid * FLOATS_PER_VARIANT;
                out[o] = diskViol[gid];
                out[o + 1] = (float) Math.toDegrees(diskTheta[gid]);
                out[o + 2] = Float.POSITIVE_INFINITY;
                out[o + 3] = 0.0f;
                out[o + 4] = Float.NaN;
            }
            for (int i = 0; i < m; i++) {
                int o = idx[i] * FLOATS_PER_VARIANT;
                out[o + 2] = sViol[i];
                out[o + 3] = (float) Math.toDegrees(sTheta[i]);
                out[o + 4] = sObj[i];
            }
            long t3 = System.nanoTime();
            batches.incrementAndGet();
            schedules.addAndGet(count);
            screened.addAndGet(m);
            diskNanos.addAndGet(t1 - t0);
            screenNanos.addAndGet(t2 - t1);
            hostNanos.addAndGet(t3 - t2);
        }
        return out;
    }

    private static void enqueueChunked(cl_kernel kernel, int items, int chunk) {
        for (int off = 0; off < items; off += chunk) {
            long size = roundUp(Math.min(chunk, items - off), 64);
            CL.clEnqueueNDRangeKernel(context.queue(), kernel, 1, new long[]{off}, new long[]{size}, null, 0, null, null);
            CL.clFinish(context.queue());
        }
    }

    private static long roundUp(long v, long m) {
        return ((v + m - 1) / m) * m;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        synchronized (LOCK) {
            releaseBatchBuffers();
            cl_mem[] bufs = {tabBuf, coefBuf, shiftBuf, axisBuf, kindBuf, groupBuf, objBuf, paramBuf, cosBuf, sinBuf};
            for (cl_mem b : bufs) CL.clReleaseMemObject(b);
            CL.clReleaseKernel(diskKernel);
            CL.clReleaseKernel(screenKernel);
        }
    }
}
