package microp.ble_426;


import java.util.HashMap;

public class GattAttributes {
    public static String SERVICE_UUID = "99366e80-cf3a-11e1-9a38-0002a5d5c51b";
    public static String CHARACTERISTIC_UUID = "98366e80-cf3a-11e1-9b39-0002a5d5c51b";
    public static String ACC_CHAR_UUID = "340a1b80-cf4b-11e1-ac36-0002a5d5c51b";
    public static String FREEFALL_CHAR_UUID = "e23e78a0-cf4a-11e1-8ffc-0002a5d5c51b";
    public static String TEMP_CHAR_UUID = "a32e5520-e477-11e2-a9e3-0002a5d5c51b";
    public  static String NOTIFICATION_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    private static HashMap<String, String> attributes = new HashMap();

    static {
        attributes.put(SERVICE_UUID, "New Service");
        attributes.put(CHARACTERISTIC_UUID, "New Char");
        attributes.put(ACC_CHAR_UUID, "ACC Char");
        attributes.put(FREEFALL_CHAR_UUID, "FreeFall Char");
        attributes.put(TEMP_CHAR_UUID, "Temperature Char");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
