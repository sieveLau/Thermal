package tk.huaj;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Arrays;
import java.util.Scanner;

import static java.lang.Thread.sleep;

public class Thermal {
    private static final int[] tempSteps = {47, 53, 55, 60, 70, 80, 84};
    private static final int tempstep = tempSteps.length;
    private static Logger logger = LogManager.getLogger("mylog");
    private FanController fanController;
    private BattController battController;
    private CPUFreqController cpuFreqController;
    private GPUFreqController gpuFreqController;


    private Thermal() {
        try {

            fanController = new FanController();
            battController = new BattController();

            cpuFreqController = new CPUFreqController();
            gpuFreqController = new GPUFreqController();
        } catch (IOException e) {
            e.printStackTrace();
            logger.warn(e);
        }

    }


    private static int temperatureReader() throws IOException {
        char[] temp = new char[2];

        FileReader reader0 = new FileReader("/sys/class/thermal/thermal_zone0/temp");
        FileReader reader1 = new FileReader("/sys/class/thermal/thermal_zone1/temp");

        reader0.read(temp);
        int temp0 = Integer.parseInt(String.valueOf(temp));
        reader1.read(temp);
        int temp1 = Integer.parseInt(String.valueOf(temp));

        return ((temp0 >= temp1) ? temp0 : temp1);

    }

    public static void main(String[] args) {
        Thermal thermal;
        thermal = new Thermal();
        while (true) {
            thermal.CoreController();


        }


    }

    private void CoreController() {
        int temperature;
        boolean batt;
        try {
            temperature = temperatureReader();
            batt = battController.getChargingState();

            fanController.setLevel(temperature);
            cpuFreqController.freqSetter(!batt, temperature);
            gpuFreqController.freqSetter(!batt, temperature);

            logger.info(String.format("temperature: %d, fanlevel: %d, cpuFreqMax: %d, gpuFreqMax: %d", temperature, fanController.getCurrenctLevel(), cpuFreqController.getCurrentMax(), gpuFreqController.getCurrentMax()));
            sleep(5000);
        } catch (Exception e) {
            e.printStackTrace();
            logger.warn(e);
        }

//        return fanController.getCurrenctLevel();
    }

    class FanController {
        private final String fanpath = "/proc/acpi/ibm/fan";
        int level;

        FanController() {

        }

        public int getCurrenctLevel() throws FileNotFoundException, NumberFormatException {
            String line = "0";
            Scanner scanner = new Scanner(new File(fanpath));
            while (scanner.hasNext()) {
                line = scanner.nextLine();
                if (line.contains("level")) {
                    break;
                }
            }
            try {
                return Integer.parseInt(String.valueOf(line.charAt(line.length() - 1)));
            } catch (Exception e) {
                return 0;
            }
        }

        private int setLevel(int temperature) throws IOException {

            for (level = 0; level < tempstep; level++) {
                if (temperature < tempSteps[level]) {
                    break;
                }
            }

            if (getCurrenctLevel() != level) {
                fancontroller(level);
            }
            return level;
        }

        private void fancontroller(int level) throws IOException {
            FileWriter fw = new FileWriter(fanpath, false);
            fw.write(String.format("level %d", level));
            fw.flush();
            fw.close();
        }
    }

    class CPUFreqController {
        private static final long limited = 1600000;
        private final int cpuN = 8;
        private final long max;

        private long[] currentMax = new long[cpuN];

        CPUFreqController() throws FileNotFoundException {
            max = new Scanner(new File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")).nextLong();
        }

        public long getCurrentMax() throws FileNotFoundException {
            Scanner reader;
            String filepath;
            for (int i = 0; i <= 7; i++) {
                filepath = String.format("/sys/devices/system/cpu/cpufreq/policy%d/scaling_max_freq", i);
                reader = new Scanner(new File(filepath));
                currentMax[i] = reader.nextLong();
            }
            Arrays.sort(currentMax);
            return currentMax[currentMax.length - 1];
        }

        private long freqSetter(boolean batt, int temperature) throws Exception {
            if (batt || (temperature > 84 && getCurrentMax() != limited)) {
                configWriter(limited);
                return limited;
            } else if (getCurrentMax() != max) {
                configWriter(max);
                return max;
            }
            return getCurrentMax();
        }

        private void configWriter(long freq) throws IOException {
            FileWriter writer;
            String filepath;
//        Process process = Runtime.getRuntime().exec(String.format("/usr/bin/cpupower frequency-set -u %d",freq));
            for (int i = 0; i <= 7; i++) {
                filepath = String.format("/sys/devices/system/cpu/cpufreq/policy%d/scaling_max_freq", i);
                writer = new FileWriter(filepath, false);
                writer.write(String.format("%d", freq));
                writer.flush();
                writer.close();
            }
        }
    }

    class GPUFreqController {
        private final String[] filepath = new String[2];
        //        private final int cpuN = 8;
        private long limited;
        private long max;
        private long min;
        private long[] currentMax;

        GPUFreqController() throws FileNotFoundException {
            max = new Scanner(new File("/sys/class/drm/card0/gt_RP0_freq_mhz")).nextLong();
            min = new Scanner(new File("/sys/class/drm/card0/gt_RP1_freq_mhz")).nextLong();
            filepath[0] = "/sys/class/drm/card0/gt_max_freq_mhz";
            filepath[1] = "/sys/class/drm/card0/gt_boost_freq_mhz";
            currentMax = new long[filepath.length];
            limited = min;
        }

        public long getCurrentMax() throws FileNotFoundException {
            Scanner reader;
            for (int i = 0; i < filepath.length; i++) {
                reader = new Scanner(new File(filepath[i]));
                currentMax[i] = reader.nextLong();
            }
            Arrays.sort(currentMax);
            return currentMax[currentMax.length - 1];
        }

        private long freqSetter(boolean batt, int temperature) throws Exception {
            if (batt || (temperature > 84 && getCurrentMax() != limited)) {
                configWriter(limited);
                return limited;
            } else if (getCurrentMax() != max) {
                configWriter(max);
                return max;
            }
            return getCurrentMax();
        }

        private void configWriter(long freq) throws IOException {
            FileWriter writer;
//            String filepath;
//        Process process = Runtime.getRuntime().exec(String.format("/usr/bin/cpupower frequency-set -u %d",freq));
            for (String s : filepath) {
//                filepath = String.format("/sys/devices/system/cpu/cpufreq/policy%d/scaling_max_freq", i);
                writer = new FileWriter(s, false);
                writer.write(String.format("%d", freq));
                writer.flush();
                writer.close();
            }
        }
    }


    class BattController {
        private static final String POWER_SUPPLY = "/sys/class/power_supply/BAT0/status";
        private Scanner scanner;

        private boolean getChargingState() throws FileNotFoundException {
            scanner = new Scanner(new File(POWER_SUPPLY));

            return !scanner.nextLine().equals("Discharging");
        }
    }
}
