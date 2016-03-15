package com.eveningoutpost.dexdrip.Models;

import java.text.DecimalFormat;
import java.util.Date;

import info.nightscout.client.data.NSCal;
import info.nightscout.client.data.NSSgv;

public class BgReading {
    public double slope;
    public double raw;
    public long timestamp;
    public double value;
    public int battery_level;
    public String units = " mg/dl";
    public String date;

    public BgReading() {}

    public BgReading(NSSgv sgv, NSCal cal) {
        slope = cal.slope;
        raw = sgv.getUnfiltered();
        timestamp = sgv.getMills();
        value = sgv.getMgdl();
        battery_level = 50;
        date = (new Date(timestamp)).toString();
    }

    public BgReading(NSSgv sgv, NSCal cal, String units) {
        this(sgv, cal);
        this.units = units;
    }

    public String valInUnit() {
        DecimalFormat formatMmol = new DecimalFormat("0.0");
        DecimalFormat formatMgdl = new DecimalFormat("0");
        if (units.equals("mg/dl")) return formatMgdl.format(value);
        else return formatMmol.format(value/18d);
    }
}
