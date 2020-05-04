# BLE-example
Basic Bluetooth Low Energy tutorial for beginners, like me.

>  I annotated both scan method with by UUID, and MAC.
>In my case, device I got cannot be searched with original method with UUID.
>
>So, if you're unable to search device of yours by UUID or MAC address, like my case, try another one.
>
>And I also notice this code was refered by two sites below.
>- https://www.bignerdranch.com/blog/bluetooth-low-energy-on-android-part-1/
>- https://steemit.com/kr/@etainclub/android-app-ble-4-ble-scan


Checklist for got trouble with scanning their own device
- Switch scan filter code with UUID or MAC Address.
- Did you changed UUIDs and MAC Address to your own device?
```java
    public static String SERVICE_STRING = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static UUID UUID_TDCS_SERVICE = UUID.fromString(SERVICE_STRING);
    public static String CHARACTERISTIC_COMMAND_STRING = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    public static UUID UUID_CTRL_COMMAND = UUID.fromString(CHARACTERISTIC_COMMAND_STRING);
    public static String CHARACTERISTIC_RESPONSE_STRING = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    public static UUID UUID_CTRL_RESPONSE = UUID.fromString(CHARACTERISTIC_RESPONSE_STRING);
    public final static String MAC_ADDR = "E7:34:CC:2B:4A:7F";
