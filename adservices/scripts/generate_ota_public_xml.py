#!/usr/bin/env python
#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import xml.etree.ElementTree as ET
import datetime
import os
from os import listdir
from os.path import isfile, join


# This script generates and updates the public.xml file for sample ota app automatically.
# Must be run after adding new strings to sample ota app.

class AdServicesOTAUtil:
    ADSERVICES_PUBLIC_RES_FILE = '../apk/publicres/values/public.xml'
    OTA_PUBLIC_RES_FILE = '../samples/ota/res/values/public.xml'
    OTA_RES_VALUES_DIR = '../samples/ota/res/values/'
    OTA_LAYOUT_DIR = "../samples/ota/res/layout"
    OTA_DRAWABLE_DIR = "../samples/ota/res/drawable"
    OTA_COLOR_DIR = "../samples/ota/res/color"

    COPYRIGHT_TEXT = f'''<?xml version="1.0" encoding="utf-8"?>
    <!-- Copyright (C) {datetime.date.today().year} The Android Open Source Project
    
        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.
    -->
    '''

    def _add_value_res(self, adservices_dict, public_xml, ota_res_values_dir):
        if (not os.path.isdir(ota_res_values_dir)):
            return
        for file in os.scandir(ota_res_values_dir):
            if (file.name == 'public.xml'):
                continue
            root_element = ET.parse(file.path).getroot()
            elem_type = file.name.split(".")[0][:-1]
            for x in root_element:
                cur_element = ET.SubElement(public_xml, 'public')
                cur_element.set('type', elem_type)
                cur_element.set('name', x.attrib['name'])
                if (elem_type, x.attrib['name']) not in adservices_dict:
                    print(f"ERROR: ({elem_type},{x.attrib['name']}) is not in adservices ")
                    exit(0)
                cur_element.set('id', adservices_dict[(elem_type, x.attrib['name'])])

    def _add_file_res(self, adservices_dict, public_xml, res_dir, res_type):
        if (not os.path.isdir(res_dir)):
            return
        res_files = [f for f in listdir(res_dir) if isfile(join(res_dir, f))]
        for file in res_files:
            cur_element = ET.SubElement(public_xml, 'public')
            cur_element.set('type', res_type)
            res_name = file.split('.')[0]
            cur_element.set('name', res_name)
            if (res_type, res_name) not in adservices_dict:
                print(f"ERROR: ({res_type},{res_name}) is not in adservices ")
                exit(0)
            cur_element.set('id', adservices_dict[(res_type, res_name)])

    def _add_id_res(self, adservices_dict, public_xml, res_dir):
        if (not os.path.isdir(res_dir)):
            return
        res_ids = set()
        for file in os.scandir(res_dir):
            ns = '{http://schemas.android.com/apk/res/android}'
            for child in ET.parse(file.path).getroot().iter():
                if (ns + 'id' in child.attrib):
                    res_ids.add(child.attrib[ns + 'id'].split('/')[1])
        res_ids = list(res_ids)
        for res_id in res_ids:
            cur_element = ET.SubElement(public_xml, 'public')
            cur_element.set('type', 'id')
            cur_element.set('name', res_id)
            if ('id', res_id) not in adservices_dict:
                print(f"ERROR: ('id',{res_id}) is not in adservices ")
                exit(0)
            cur_element.set('id', adservices_dict[('id', res_id)])

    def update_ota_public_xml(self, adservices_public_res=ADSERVICES_PUBLIC_RES_FILE,
                              ota_public_res=OTA_PUBLIC_RES_FILE,
                              ota_res_values_dir=OTA_RES_VALUES_DIR,
                              ota_layout_dir=OTA_LAYOUT_DIR,
                              ota_drawable_dir=OTA_DRAWABLE_DIR,
                              ota_color_dir=OTA_COLOR_DIR):
        adservices_dict = {}
        adservices_public_xml = ET.parse(adservices_public_res).getroot()
        for x in adservices_public_xml:
            adservices_dict[(x.attrib['type'], x.attrib['name'])] = x.attrib['id']

        public_xml = ET.Element('resources')

        self._add_value_res(adservices_dict, public_xml, ota_res_values_dir)
        self._add_file_res(adservices_dict, public_xml, ota_drawable_dir, 'drawable')
        self._add_file_res(adservices_dict, public_xml, ota_color_dir, 'color')
        self._add_file_res(adservices_dict, public_xml, ota_layout_dir, 'layout')
        self._add_id_res(adservices_dict, public_xml, ota_layout_dir)

        ET.indent(public_xml, space='    ')
        if os.path.exists(ota_public_res):
            os.remove(ota_public_res)
        with open(ota_public_res, 'w') as f:
            f.write(self.COPYRIGHT_TEXT)
            f.write(ET.tostring(public_xml, encoding="unicode"))


if __name__ == "__main__":
    util = AdServicesOTAUtil()
    util.update_ota_public_xml()
