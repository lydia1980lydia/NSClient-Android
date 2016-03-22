package info.nightscout.client.data;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by mike on 10.02.2016.
 *
 * {"mgdl":105,"mills":1455136282375,"device":"xDrip-BluetoothWixel","direction":"Flat","filtered":98272,"unfiltered":98272,"noise":1,"rssi":100}
 */
public class NSSgv {
    private JSONObject data;

    public NSSgv(JSONObject obj) {
        this.data = obj;
    }

    private String getStringOrNull(String key) {
        String ret = null;
        if (data.has(key)) {
            try {
                ret = data.getString(key);
            } catch (JSONException e) {
            }
        }
        return ret;
    };

    private Integer getIntegerOrZero(String key) {
        Integer ret = 0;
        if (data.has(key)) {
            try {
                ret = data.getInt(key);
            } catch (JSONException e) {
            }
        }
        return ret;
    };

    private Long getLongOrNull(String key) {
        Long ret = null;
        if (data.has(key)) {
            try {
                ret = data.getLong(key);
            } catch (JSONException e) {
            }
        }
        return ret;
    };

    public JSONObject getData () { return data; }
    public Integer getMgdl () { return getIntegerOrZero("mgdl"); }
    public Integer getFiltered () { return getIntegerOrZero("filtered"); }
    public Integer getUnfiltered () { return getIntegerOrZero("unfiltered"); }
    public Integer getNoise () { return getIntegerOrZero("noise"); }
    public Integer getRssi () { return getIntegerOrZero("rssi"); }
    public Long getMills () { return getLongOrNull("mills"); }
    public String getDevice () { return getStringOrNull("device"); }
    public String getDirection () { return getStringOrNull("direction"); }

}
