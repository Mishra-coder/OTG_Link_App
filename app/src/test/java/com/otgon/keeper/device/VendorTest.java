package com.otgon.keeper.device;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VendorTest {

    @Test
    public void colorOsBrands_mapToOplus() {
        assertEquals(Vendor.OPLUS, Vendor.from("realme", "realme"));
        assertEquals(Vendor.OPLUS, Vendor.from("OPPO", "OPPO"));
        assertEquals(Vendor.OPLUS, Vendor.from("OnePlus", "OnePlus"));
    }

    @Test
    public void subBrands_mapToParentVendor() {
        assertEquals(Vendor.VIVO, Vendor.from("vivo", "iQOO"));
        assertEquals(Vendor.XIAOMI, Vendor.from("Xiaomi", "POCO"));
        assertEquals(Vendor.XIAOMI, Vendor.from("Xiaomi", "Redmi"));
    }

    @Test
    public void unknownBrand_fallsBackToOther() {
        assertEquals(Vendor.OTHER, Vendor.from("Google", "google"));
    }
}
