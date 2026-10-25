package de.legoshi.parkourcalc.gpu;

import static org.jocl.CL.*;

import java.util.ArrayList;
import java.util.List;
import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

public final class OpenClContext {

	public static final String DEVICE_PROPERTY = "pkc.gpu.device";

	private final cl_platform_id platform;
	private final cl_device_id device;
	private final cl_context context;
	private final cl_command_queue queue;
	private final List<cl_program> programs = new ArrayList<cl_program>();

	public OpenClContext() {
		this(System.getProperty(DEVICE_PROPERTY));
	}

	public OpenClContext(String deviceNameSubstring) {
		CL.setExceptionsEnabled(true);
		this.device = pickDevice(deviceNameSubstring);
		this.platform = platformOf(device);
		cl_context_properties props = new cl_context_properties();
		props.addProperty(CL_CONTEXT_PLATFORM, platform);
		this.context = clCreateContext(props, 1, new cl_device_id[] {device}, null, null, null);
		this.queue = clCreateCommandQueue(context, device, 0, null);
	}

	public cl_platform_id platform() {
		return platform;
	}

	public cl_device_id device() {
		return device;
	}

	public cl_context context() {
		return context;
	}

	public cl_command_queue queue() {
		return queue;
	}

	public String deviceName() {
		return deviceString(device, CL_DEVICE_NAME);
	}

	public cl_program buildProgram(String source) {
		return buildProgram(source, "");
	}

	public cl_program buildProgram(String source, String options) {
		cl_program program = clCreateProgramWithSource(context, 1, new String[] {source}, null, null);
		int err;
		try {
			err = clBuildProgram(program, 1, new cl_device_id[] {device}, options, null, null);
		} catch (RuntimeException e) {
			String log = buildLog(program);
			clReleaseProgram(program);
			throw new IllegalStateException("OpenCL build failed on " + deviceName() + ":\n" + log, e);
		}
		if (err != CL_SUCCESS) {
			String log = buildLog(program);
			clReleaseProgram(program);
			throw new IllegalStateException("OpenCL build failed (" + stringFor_errorCode(err) + ") on " + deviceName() + ":\n" + log);
		}
		programs.add(program);
		return program;
	}

	private String buildLog(cl_program program) {
		long[] size = new long[1];
		clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, size);
		byte[] buf = new byte[(int) size[0]];
		clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, buf.length, Pointer.to(buf), null);
		return new String(buf, 0, Math.max(0, buf.length - 1)).trim();
	}

	public void release() {
		for (cl_program p : programs) clReleaseProgram(p);
		programs.clear();
		clReleaseCommandQueue(queue);
		clReleaseContext(context);
	}

	public static cl_platform_id[] platforms() {
		int[] count = new int[1];
		clGetPlatformIDs(0, null, count);
		cl_platform_id[] ids = new cl_platform_id[count[0]];
		if (ids.length > 0) clGetPlatformIDs(ids.length, ids, null);
		return ids;
	}

	public static cl_device_id[] devices(cl_platform_id platform) {
		int[] count = new int[1];
		try {
			clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, null, count);
		} catch (RuntimeException e) {
			return new cl_device_id[0];
		}
		cl_device_id[] ids = new cl_device_id[count[0]];
		if (ids.length > 0) clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, ids.length, ids, null);
		return ids;
	}

	private static cl_device_id pickDevice(String nameSubstring) {
		boolean named = nameSubstring != null && !nameSubstring.isEmpty();
		cl_device_id firstGpu = null;
		cl_device_id firstAny = null;
		for (cl_platform_id p : platforms()) {
			for (cl_device_id d : devices(p)) {
				if (firstAny == null) firstAny = d;
				if (named && deviceString(d, CL_DEVICE_NAME).toLowerCase().contains(nameSubstring.toLowerCase())) return d;
				if (firstGpu == null && (deviceLong(d, CL_DEVICE_TYPE) & CL_DEVICE_TYPE_GPU) != 0) firstGpu = d;
			}
		}
		if (named) throw new IllegalStateException("no OpenCL device matches " + DEVICE_PROPERTY + "=" + nameSubstring);
		cl_device_id chosen = firstGpu != null ? firstGpu : firstAny;
		if (chosen == null) throw new IllegalStateException("no OpenCL device found");
		return chosen;
	}

	private static cl_platform_id platformOf(cl_device_id device) {
		cl_platform_id p = new cl_platform_id();
		clGetDeviceInfo(device, CL_DEVICE_PLATFORM, Sizeof.cl_platform_id, Pointer.to(p), null);
		return p;
	}

	public static String platformString(cl_platform_id platform, int param) {
		long[] size = new long[1];
		clGetPlatformInfo(platform, param, 0, null, size);
		byte[] buf = new byte[(int) size[0]];
		clGetPlatformInfo(platform, param, buf.length, Pointer.to(buf), null);
		return new String(buf, 0, Math.max(0, buf.length - 1)).trim();
	}

	public static String deviceString(cl_device_id device, int param) {
		long[] size = new long[1];
		clGetDeviceInfo(device, param, 0, null, size);
		byte[] buf = new byte[(int) size[0]];
		clGetDeviceInfo(device, param, buf.length, Pointer.to(buf), null);
		return new String(buf, 0, Math.max(0, buf.length - 1)).trim();
	}

	public static long deviceLong(cl_device_id device, int param) {
		long[] v = new long[1];
		clGetDeviceInfo(device, param, Sizeof.cl_long, Pointer.to(v), null);
		return v[0];
	}

	public static int deviceInt(cl_device_id device, int param) {
		int[] v = new int[1];
		clGetDeviceInfo(device, param, Sizeof.cl_int, Pointer.to(v), null);
		return v[0];
	}

	public static long deviceSizeT(cl_device_id device, int param) {
		long[] v = new long[1];
		clGetDeviceInfo(device, param, Sizeof.size_t, Pointer.to(v), null);
		return v[0];
	}
}
