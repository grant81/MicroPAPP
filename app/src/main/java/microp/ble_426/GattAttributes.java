package microp.ble_426;


import java.util.HashMap;

public class GattAttributes {
    public static String SERVICE_UUID = "99366e80-cf3a-11e1-9a38-0002a5d5c51b";
    public static String CHARACTERISTIC_UUID = "98366e80-cf3a-11e1-9b39-0002a5d5c51b";
    public  static String CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    private static HashMap<String, String> attributes = new HashMap();

    static {
        attributes.put(SERVICE_UUID, "New Service");
        attributes.put(CHARACTERISTIC_UUID, "New Char");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
