package de.legoshi.parkourcalc.gpu;

import static org.jocl.CL.*;

import java.util.Locale;
import java.util.Random;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

public final class OpenClProbe {

	private static final int N = 16 * 1024 * 1024;

	private static final String KERNEL =
			"__kernel void vadd(__global const float* a, __global const float* b, __global float* c, int n) {\n"
			+ "    int i = get_global_id(0);\n"
			+ "    if (i < n) c[i] = a[i] + b[i];\n"
			+ "}\n";

	public static void main(String[] args) {
		setExceptionsEnabled(true);
		listDevices();
		OpenClContext ctx = new OpenClContext();
		System.out.println();
		System.out.println("Using device: " + ctx.deviceName());
		try {
			vectorAdd(ctx);
		} finally {
			ctx.release();
		}
	}

	private static void listDevices() {
		cl_platform_id[] platforms = OpenClContext.platforms();
		System.out.println("OpenCL platforms: " + platforms.length);
		for (int pi = 0; pi < platforms.length; pi++) {
			cl_platform_id p = platforms[pi];
			System.out.println("[" + pi + "] " + OpenClContext.platformString(p, CL_PLATFORM_NAME)
					+ " | " + OpenClContext.platformString(p, CL_PLATFORM_VENDOR)
					+ " | " + OpenClContext.platformString(p, CL_PLATFORM_VERSION));
			cl_device_id[] devices = OpenClContext.devices(p);
			for (int di = 0; di < devices.length; di++) {
				cl_device_id d = devices[di];
				String extensions = OpenClContext.deviceString(d, CL_DEVICE_EXTENSIONS);
				System.out.println("    [" + pi + "." + di + "] " + OpenClContext.deviceString(d, CL_DEVICE_NAME)
						+ " (" + typeName(OpenClContext.deviceLong(d, CL_DEVICE_TYPE)) + ")");
				System.out.println("        vendor:          " + OpenClContext.deviceString(d, CL_DEVICE_VENDOR));
				System.out.println("        version:         " + OpenClContext.deviceString(d, CL_DEVICE_VERSION)
						+ " / driver " + OpenClContext.deviceString(d, CL_DRIVER_VERSION));
				System.out.println("        compute units:   " + OpenClContext.deviceInt(d, CL_DEVICE_MAX_COMPUTE_UNITS));
				System.out.println("        max work group:  " + OpenClContext.deviceSizeT(d, CL_DEVICE_MAX_WORK_GROUP_SIZE));
				System.out.println("        global memory:   " + mib(OpenClContext.deviceLong(d, CL_DEVICE_GLOBAL_MEM_SIZE)));
				System.out.println("        fp64:            " + (extensions.contains("cl_khr_fp64") ? "yes" : "no"));
			}
		}
	}

	private static void vectorAdd(OpenClContext ctx) {
		float[] a = new float[N];
		float[] b = new float[N];
		float[] c = new float[N];
		Random rnd = new Random(42);
		for (int i = 0; i < N; i++) {
			a[i] = rnd.nextFloat();
			b[i] = rnd.nextFloat();
		}

		cl_program program = ctx.buildProgram(KERNEL);
		cl_kernel kernel = clCreateKernel(program, "vadd", null);
		long bytes = (long) N * Sizeof.cl_float;
		cl_mem memA = clCreateBuffer(ctx.context(), CL_MEM_READ_ONLY, bytes, null, null);
		cl_mem memB = clCreateBuffer(ctx.context(), CL_MEM_READ_ONLY, bytes, null, null);
		cl_mem memC = clCreateBuffer(ctx.context(), CL_MEM_WRITE_ONLY, bytes, null, null);

		long t0 = System.nanoTime();
		clEnqueueWriteBuffer(ctx.queue(), memA, CL_TRUE, 0, bytes, Pointer.to(a), 0, null, null);
		clEnqueueWriteBuffer(ctx.queue(), memB, CL_TRUE, 0, bytes, Pointer.to(b), 0, null, null);
		clFinish(ctx.queue());
		long t1 = System.nanoTime();

		clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(memA));
		clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(memB));
		clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(memC));
		clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[] {N}));
		long[] global = new long[] {N};
		long[] local = new long[] {256};

		clEnqueueNDRangeKernel(ctx.queue(), kernel, 1, null, global, local, 0, null, null);
		clFinish(ctx.queue());
		long t2 = System.nanoTime();
		clEnqueueNDRangeKernel(ctx.queue(), kernel, 1, null, global, local, 0, null, null);
		clFinish(ctx.queue());
		long t3 = System.nanoTime();

		clEnqueueReadBuffer(ctx.queue(), memC, CL_TRUE, 0, bytes, Pointer.to(c), 0, null, null);
		clFinish(ctx.queue());
		long t4 = System.nanoTime();

		long cpu0 = System.nanoTime();
		int mismatches = 0;
		for (int i = 0; i < N; i++) {
			if (c[i] != a[i] + b[i]) mismatches++;
		}
		long cpu1 = System.nanoTime();

		System.out.println("Vector add over " + N + " floats (" + mib(bytes) + " per buffer)");
		System.out.println("    host -> device (2 buffers): " + ms(t1 - t0) + " ms");
		System.out.println("    kernel, first run:          " + ms(t2 - t1) + " ms");
		System.out.println("    kernel, second run:         " + ms(t3 - t2) + " ms");
		System.out.println("    device -> host (1 buffer):  " + ms(t4 - t3) + " ms");
		System.out.println("    cpu verify loop:            " + ms(cpu1 - cpu0) + " ms");
		System.out.println("    result: " + (mismatches == 0 ? "OK" : "MISMATCH (" + mismatches + " elements)"));

		clReleaseMemObject(memA);
		clReleaseMemObject(memB);
		clReleaseMemObject(memC);
		clReleaseKernel(kernel);
		if (mismatches != 0) throw new IllegalStateException("GPU result differs from CPU");
	}

	private static String typeName(long type) {
		if ((type & CL_DEVICE_TYPE_GPU) != 0) return "GPU";
		if ((type & CL_DEVICE_TYPE_CPU) != 0) return "CPU";
		if ((type & CL_DEVICE_TYPE_ACCELERATOR) != 0) return "ACCELERATOR";
		return "OTHER";
	}

	private static String mib(long bytes) {
		return String.format(Locale.ROOT, "%.0f MiB", bytes / (1024.0 * 1024.0));
	}

	private static String ms(long nanos) {
		return String.format(Locale.ROOT, "%.2f", nanos / 1e6);
	}
}
