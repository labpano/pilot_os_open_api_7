package com.pi.pano;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

final class ResourceAnalysisUtil {

    static String getGpuInfoString() {
        String gpuBusyPercentage = readLine("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", "0");
        int gpuClk = Integer.parseInt(readLine("/sys/class/kgsl/kgsl-3d0/gpuclk", "0")) / 1000000;
        int minFreq = Integer.parseInt(readLine("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", "0")) / 1000000;
        int maxFreq = Integer.parseInt(readLine("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", "0")) / 1000000;
        return String.format(Locale.getDefault(), "%s freq: %d[%d-%d]", gpuBusyPercentage, gpuClk, minFreq, maxFreq);
    }

    static String getBatteryInfoString() {
        float batteryAnalysis = Integer.parseInt(readLine("/sys/class/power_supply/battery/current_now", "0")) / 1000.0f;
        return String.format(Locale.getDefault(), "%.2fmA", batteryAnalysis);
    }

    static String getCpuTemperatureInfoString() {
        float cpuTemperature3 = CpuTemperatureReader.getCpuTemperature3();
        return String.format(Locale.getDefault(), "%.2f°C", cpuTemperature3);
    }

    public static String getCpuInfoString() {
        float totalCpuTime = getTotalCpuTime();
        float deltaTotal = totalCpuTime - mLastTotalCpuTime;
        if (deltaTotal == 0) {
            return "";
        }
        float appCpuTime = getAppCpuTime();
        float useCpuTime = getUseCpuTime();
        float appCpuRate = 100 * (appCpuTime - mLastAppCpuTime) / deltaTotal;
        float useCpuRate = 100 * (useCpuTime - mLaseUseCpuTime) / deltaTotal;
        mLastTotalCpuTime = totalCpuTime;
        mLastAppCpuTime = appCpuTime;
        mLaseUseCpuTime = useCpuTime;
        int cpu6Clk = Integer.parseInt(readLine("/sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq", "0")) / 1000;
        int cpu0Clk = Integer.parseInt(readLine("/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq", "0")) / 1000;
        return String.format(Locale.getDefault(), "app:%.2f%% all:%.2f%% CPU6:%dM CPU0:%dM", appCpuRate, useCpuRate, cpu6Clk, cpu0Clk);
    }

    private static float mLastTotalCpuTime;
    private static float mLastAppCpuTime;
    private static float mLaseUseCpuTime;

    // 获取系统总CPU使用时间
    public static float getTotalCpuTime() {
        String load = readLine("/proc/stat", null);
        if (null != load) {
            String[] cpuInfos = load.split(" ");
            return Long.parseLong(cpuInfos[2])
                    + Long.parseLong(cpuInfos[3])
                    + Long.parseLong(cpuInfos[4])
                    + Long.parseLong(cpuInfos[6])
                    + Long.parseLong(cpuInfos[5])
                    + Long.parseLong(cpuInfos[7])
                    + Long.parseLong(cpuInfos[8]);
        }
        return 0;
    }

    // 获取应用占用的CPU时间
    public static float getAppCpuTime() {
        int pid = android.os.Process.myPid();
        String load = readLine("/proc/" + pid + "/stat", null);
        if (null != load) {
            String[] info = load.split(" ");
            return Long.parseLong(info[13])
                    + Long.parseLong(info[14])
                    + Long.parseLong(info[15])
                    + Long.parseLong(info[16]);
        }
        return 0;
    }

    public static float getUseCpuTime() {
        String load = readLine("/proc/stat", null);
        if (null != load) {
            String[] info = load.split(" ");
            return Long.parseLong(info[2])
                    + Long.parseLong(info[3])
                    + Long.parseLong(info[4])
                    + Long.parseLong(info[6])
                    + Long.parseLong(info[7])
                    + Long.parseLong(info[8]);
        }
        return 0;
    }


    public static String getMemoryInfoString() {
        int appInfo = getAppInfo();
        int allInfo = getAllInfo();
        return String.format(Locale.getDefault(), "%dMB free:%dMB", appInfo, allInfo);
    }

    private static int getAllInfo() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String load;
            while ((load = reader.readLine()) != null) {
                load = load.replace(" ", "");
                String[] info = load.split("[: k K]");
                if (info[0].equals("MemFree")) {
                    return Integer.parseInt(info[1].trim()) / 1024;
                }
            }
        } catch (Exception ignore) {
        }
        return 0;
    }

    private static int getAppInfo() {
        int pid = android.os.Process.myPid();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/" + pid + "/status"))) {
            String load;
            while ((load = reader.readLine()) != null) {
                load = load.replace(" ", "");
                String[] info = load.split("[: k K]");
                if (info[0].equals("VmRSS")) {
                    return Integer.parseInt(info[1].trim()) / 1024;
                }
            }
        } catch (Exception ignore) {
        }
        return 0;
    }

    private static String readLine(String fileName, String def) {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            return reader.readLine().trim();
        } catch (Exception ignore) {
            //ignore.printStackTrace();
        }
        return def;
    }
}
