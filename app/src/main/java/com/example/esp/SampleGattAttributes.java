/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.esp;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 *
 *  BLE 디바이스와의 상호작용 중에 참조될 수 있는 여러 GATT 서비스와 특성의 예시 이름을 제공합니다.
 *  예를 들어, "Heart Rate Service"나 "Device Information Service" 같은 GATT 서비스의 의미를 쉽게 이해할 수 있도록 UUID에 "이름"을 매핑합니다.
 *  이는 개발자가 코드 내에서 직접 UUID를 사용하는 대신 이해하기 쉬운 이름으로 GATT 서비스와 특성을 참조할 수 있게 해 줍니다.
 */
public class SampleGattAttributes {
    // UUID와 해당 이름을 매핑하기 위한 HashMap
    private static HashMap<String, String> attributes = new HashMap();
    // 심박수 측정 특성의 UUID
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    // 클라이언트 특성 구성(알림 활성화/비활성화)의 UUID
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    // added
    //public static String BATTERY_LEVEL_CHARACTERISTIC_UUID = "00002a19-0000-1000-8000-00805f9b34fb";
    //public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String CUSTOM_RENAME_SERVICE  = "000000ff-0000-1000-8000-00805f9b34fb";
    public static String CUSTOM_RENAME_CHARACTERISTIC = "0000ff01-0000-1000-8000-00805f9b34fb";

    static {
        // 기본적으로 제공되는 GATT 서비스와 특성을 정의합니다.
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // added attributes.put("0000180f-0000-1000-8000-00805f9b34fb", "BATTERY_LEVEL_SERVICE");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        // added attributes.put(BATTERY_LEVEL_CHARACTERISTIC_UUID, "Battery Level Measurement");

        // 맞춤 서비스와 특성의 이름 추가 (이름은 예시로만 사용하고, 실제 적용 시 적절한 값으로 대체해야 합니다)
        attributes.put(CUSTOM_RENAME_SERVICE, "Custom ReName");
        attributes.put(CUSTOM_RENAME_CHARACTERISTIC, "Custom ReName Characteristic");
    }


    // 주어진 UUID에 해당하는 이름을 조회합니다. 이름이 존재하지 않는 경우 기본 이름을 반환합니다.
    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
