package com.pi.pano;

/**
 * cpu温度读取
 */
public final class CpuTemperatureReader {
    /**
     * 获取cpu温度。
     *
     * @return 摄氏温度数值*1000
     */
    public static String getCpuTemperature() {
        return Utils.readContentSingleLine("/sys/class/thermal/thermal_zone23/temp");
    }

    /**
     * 获取cpu温度。
     *
     * @return 摄氏温度数值
     */
    public static int getCpuTemperature2() {
        try {
            String temperature = getCpuTemperature();
            return Integer.parseInt(temperature) / 1000;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * 获取cpu温度。
     *
     * @return 摄氏温度数值
     */
    public static float getCpuTemperature3() {
        try {
            String temperature = getCpuTemperature();
            return Integer.parseInt(temperature) / 1000f;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }
}
