#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import unittest
import xml.etree.ElementTree as ET
import os
import shutil

from generate_adservices_public_xml import AdServicesUiUtil
from generate_adservices_public_xml import ResourceCategory
from generate_ota_public_xml import AdServicesOTAUtil


class AdServiceUiTests(unittest.TestCase):
    TEST_DIR = 'test_res/values/'
    TEST_LAYOUT_DIR = 'test_res/layout/'

    TEST_OTA_DIR = 'test_ota_res/values/'
    TEST_OTA_LAYOUT_DIR = 'test_ota_res/layout/'
    TEST_OTA_DRAWABLE_DIR = 'test_ota_res/drawable/'
    TEST_OTA_COLOR_DIR = 'test_ota_res/color/'

    TEST_STRINGS_FILE = 'test_res/values/strings.xml'
    TEST_STRINGS_XML = '''
    <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <string name="permlab_accessAdServicesTopics">access AdServices Topics API</string>
    <string name="permdesc_accessAdServicesTopics">Allows an application to access AdServices Topics API.</string>
    </resources>
    '''

    TEST_OTA_STRINGS_FILE = 'test_ota_res/values/strings.xml'
    TEST_OTA_STRINGS_XML = '''
    <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <string name="permlab_accessAdServicesTopics">Access AdServices Topics API</string>
    </resources>
    '''

    TEST_DIMENS_FILE = 'test_res/values/dimens.xml'
    TEST_DIMENS_XML = '''
    <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <dimen name="disabled_button_alpha" format="float">0.7</dimen>
    <dimen name="enabled_button_alpha" format="float">0.5</dimen>
    </resources>
    '''

    TEST_OTA_DIMENS_FILE = 'test_ota_res/values/dimens.xml'
    TEST_OTA_DIMENS_XML = '''
    <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <dimen name="disabled_button_alpha" format="float">0.3</dimen>
    </resources>
    '''

    TEST_LAYOUT_FILE = 'test_res/layout/layout.xml'
    TEST_LAYOUT_XML = '''
    <LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/fragment_container_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
    </LinearLayout>
    '''

    TEST_OTA_LAYOUT_FILE = 'test_ota_res/layout/layout.xml'
    TEST_OTA_LAYOUT_XML = '''
    <LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="horizontal">
    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/fragment_container_view"
        android:layout_width="wrap_content"
        android:layout_height="match_parent" />
    </LinearLayout>
    '''

    TEST_STRINGS_MAP_START = '0x7f017fff'
    TEST_DIMENS_MAP_START = '0x7f027fff'
    TEST_LAYOUT_MAP_START = '0x7f037fff'
    TEST_ID_MAP_START = '0x7f047fff'

    TEST_PUBLIC_FILE = 'test_res/values/public.xml'
    TEST_PUBLIC_XML = '''
    <resources>
    <public type="string" name="permlab_accessAdServicesTopics" id="0x7f017fff" />
    <public type="string" name="permdesc_accessAdServicesTopics" id="0x7f017ffe" />
    <public type="dimen" name="disabled_button_alpha" id="0x7f027fff" />
    <public type="dimen" name="enabled_button_alpha" id="0x7f027ffe" />
    </resources>
    '''

    TEST_OTA_PUBLIC_FILE = 'test_ota_res/values/public.xml'
    TEST_OTA_PUBLIC_XML = '''
    <resources>
    <public type="string" name="permlab_accessAdServicesTopics" id="0x7f017fff" />
    <public type="dimen" name="disabled_button_alpha" id="0x7f027fff" />
    </resources>
    '''

    util = AdServicesUiUtil()
    ota_util = AdServicesOTAUtil()

    def _write(self, xml, filepath):
        f = open(filepath, "w")
        f.write(xml)
        f.close()

    def _generate_test_files(self):
        os.makedirs(self.TEST_DIR, exist_ok=True)
        os.makedirs(self.TEST_LAYOUT_DIR, exist_ok=True)

        self._write(self.TEST_STRINGS_XML, self.TEST_STRINGS_FILE)
        self._write(self.TEST_DIMENS_XML, self.TEST_DIMENS_FILE)
        self._write(self.TEST_PUBLIC_XML, self.TEST_PUBLIC_FILE)

    def _generate_ota_test_files(self):
        os.makedirs(self.TEST_OTA_DIR, exist_ok=True)
        os.makedirs(self.TEST_OTA_LAYOUT_DIR, exist_ok=True)

        self._write(self.TEST_OTA_STRINGS_XML, self.TEST_OTA_STRINGS_FILE)
        self._write(self.TEST_OTA_DIMENS_XML, self.TEST_OTA_DIMENS_FILE)
        self._write(self.TEST_OTA_PUBLIC_XML, self.TEST_OTA_PUBLIC_FILE)

    def _delete_dir(self, dir):
        if os.path.exists(dir):
            shutil.rmtree(dir)

    def _delete_all_test_files(self):
        self._delete_dir('test_res')
        self._delete_dir('test_ota_res')
        self._delete_dir('__pycache__')

    def _update_res_xml(self, res_dir, res_type, n):
        root = ET.parse(res_dir).getroot()
        for res_name in [f"test{res_type}{i}" for i in range(n)]:
            added_element = ET.SubElement(root, res_type)
            added_element.set('name', res_name)

        ET.indent(root, space='    ')
        with open(res_dir, "w+") as file:
            file.write(ET.tostring(root, encoding="unicode"))

    def _test_util_update_public_xml(self, new_res_count, public_xml, public_xml_dir):
        old_root = ET.ElementTree(ET.fromstring(public_xml)).getroot()
        old_mapping = {node.attrib['name']: node.attrib['id'] for node in old_root}

        root = ET.parse(public_xml_dir).getroot()
        mapping = {node.attrib['name']: node.attrib['id'] for node in root}

        assert (len(old_mapping) + new_res_count == len(mapping))
        assert (len(mapping) == len(set(mapping.values())))
        for name, _id in mapping.items():
            if name in old_mapping:
                assert (_id == old_mapping[name])

    def _test_util_compare_public_xmls(self, adservices_public_xml_dir, ota_public_xml_dir):
        adservices_root = ET.parse(adservices_public_xml_dir).getroot()
        adservices_mapping = {node.attrib['name']: node.attrib['id'] for node in adservices_root}

        ota_root = ET.parse(ota_public_xml_dir).getroot()
        ota_mapping = {node.attrib['name']: node.attrib['id'] for node in ota_root}

        assert (len(adservices_mapping) >= len(ota_mapping))
        assert (len(adservices_mapping) == len(set(adservices_mapping.values())))
        assert (len(ota_mapping) == len(set(ota_mapping.values())))
        for name, _id in ota_mapping.items():
            assert (_id == adservices_mapping[name])

    def test_adding_adservices_strings(self):
        self._generate_test_files()

        new_strings_count = 5
        self._update_res_xml(self.TEST_STRINGS_FILE, 'string', new_strings_count)
        self.util.update_public_xml(self.TEST_STRINGS_FILE, 'string', ResourceCategory.Value,
                                    self.TEST_DIMENS_MAP_START, self.TEST_PUBLIC_FILE)

        self._test_util_update_public_xml(new_strings_count, self.TEST_PUBLIC_XML,
                                          self.TEST_PUBLIC_FILE)

        self._delete_all_test_files()

    def test_adding_adservices_dimens(self):
        self._generate_test_files()

        new_dimens_count = 2
        self._update_res_xml(self.TEST_DIMENS_FILE, 'dimen', new_dimens_count)
        self.util.update_public_xml(self.TEST_DIMENS_FILE, 'dimen', ResourceCategory.Value,
                                    self.TEST_DIMENS_MAP_START, self.TEST_PUBLIC_FILE)

        self._test_util_update_public_xml(new_dimens_count, self.TEST_PUBLIC_XML,
                                          self.TEST_PUBLIC_FILE)

        self._delete_all_test_files()

    def test_adding_adservices_layouts(self):
        self._generate_test_files()

        new_layout_count = 1
        self._write(self.TEST_LAYOUT_XML, self.TEST_LAYOUT_FILE)
        self.util.update_public_xml(self.TEST_LAYOUT_DIR, 'layout', ResourceCategory.File,
                                    self.TEST_LAYOUT_MAP_START, self.TEST_PUBLIC_FILE)

        self._test_util_update_public_xml(new_layout_count, self.TEST_PUBLIC_XML,
                                          self.TEST_PUBLIC_FILE)

        self._delete_all_test_files()

    def test_adding_adservices_multiple_res(self):
        self._generate_test_files()

        new_strings_count = 3
        self._update_res_xml(self.TEST_STRINGS_FILE, 'string', new_strings_count)
        self.util.update_public_xml(self.TEST_STRINGS_FILE, 'string', ResourceCategory.Value,
                                    self.TEST_DIMENS_MAP_START, self.TEST_PUBLIC_FILE)

        self._test_util_update_public_xml(new_strings_count, self.TEST_PUBLIC_XML,
                                          self.TEST_PUBLIC_FILE)

        new_dimens_count = 2
        self._update_res_xml(self.TEST_DIMENS_FILE, 'dimen', new_dimens_count)
        self.util.update_public_xml(self.TEST_DIMENS_FILE, 'dimen', ResourceCategory.Value,
                                    self.TEST_DIMENS_MAP_START, self.TEST_PUBLIC_FILE)

        self._test_util_update_public_xml(new_strings_count + new_dimens_count,
                                          self.TEST_PUBLIC_XML,
                                          self.TEST_PUBLIC_FILE)

        new_layout_count = 1
        self._write(self.TEST_LAYOUT_XML, self.TEST_LAYOUT_FILE)
        self.util.update_public_xml(self.TEST_LAYOUT_DIR, 'layout', ResourceCategory.File,
                                    self.TEST_LAYOUT_MAP_START, self.TEST_PUBLIC_FILE)

        self._test_util_update_public_xml(new_strings_count + new_dimens_count + new_layout_count,
                                          self.TEST_PUBLIC_XML,
                                          self.TEST_PUBLIC_FILE)

        self._delete_all_test_files()

    def test_adding_ota_strings(self):
        self._generate_test_files()
        self._generate_ota_test_files()

        new_strings_count = 5
        self._update_res_xml(self.TEST_STRINGS_FILE, 'string', new_strings_count)
        new_ota_strings_count = 3
        self._update_res_xml(self.TEST_OTA_STRINGS_FILE, 'string', new_ota_strings_count)

        self.util.update_public_xml(self.TEST_STRINGS_FILE, 'string', ResourceCategory.Value,
                                    self.TEST_DIMENS_MAP_START, self.TEST_PUBLIC_FILE)
        self.ota_util.update_ota_public_xml(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE,
                                            self.TEST_OTA_DIR, self.TEST_OTA_LAYOUT_DIR,
                                            self.TEST_OTA_DRAWABLE_DIR, self.TEST_OTA_COLOR_DIR)

        self._test_util_compare_public_xmls(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE)

        self._delete_all_test_files()

    def test_adding_ota_dimens(self):
        self._generate_test_files()
        self._generate_ota_test_files()

        new_dimens_count = 2
        self._update_res_xml(self.TEST_DIMENS_FILE, 'dimen', new_dimens_count)
        new_ota_dimens_count = 1
        self._update_res_xml(self.TEST_OTA_DIMENS_FILE, 'dimen', new_ota_dimens_count)

        self.util.update_public_xml(self.TEST_DIMENS_FILE, 'dimen', ResourceCategory.Value,
                                    self.TEST_DIMENS_MAP_START, self.TEST_PUBLIC_FILE)
        self.ota_util.update_ota_public_xml(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE,
                                            self.TEST_OTA_DIR, self.TEST_OTA_LAYOUT_DIR,
                                            self.TEST_OTA_DRAWABLE_DIR, self.TEST_OTA_COLOR_DIR)

        self._test_util_compare_public_xmls(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE)

        self._delete_all_test_files()

    def test_adding_ota_layout(self):
        self._generate_test_files()
        self._generate_ota_test_files()

        self._write(self.TEST_LAYOUT_XML, self.TEST_LAYOUT_FILE)
        self._write(self.TEST_OTA_LAYOUT_XML, self.TEST_OTA_LAYOUT_FILE)

        self.util.update_public_xml(self.TEST_LAYOUT_DIR, 'layout', ResourceCategory.File,
                                    self.TEST_LAYOUT_MAP_START, self.TEST_PUBLIC_FILE)
        self.util.update_public_xml(self.TEST_LAYOUT_DIR, 'id', ResourceCategory.Id,
                                    self.TEST_ID_MAP_START, self.TEST_PUBLIC_FILE)

        self.ota_util.update_ota_public_xml(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE,
                                            self.TEST_OTA_DIR, self.TEST_OTA_LAYOUT_DIR,
                                            self.TEST_OTA_DRAWABLE_DIR, self.TEST_OTA_COLOR_DIR)

        self._test_util_compare_public_xmls(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE)

        self._delete_all_test_files()

    def test_adding_ota_multiple_res(self):
        self._generate_test_files()
        self._generate_ota_test_files()

        new_strings_count = 3
        self._update_res_xml(self.TEST_STRINGS_FILE, 'string', new_strings_count)
        new_ota_strings_count = 2
        self._update_res_xml(self.TEST_OTA_STRINGS_FILE, 'string', new_ota_strings_count)

        self.util.update_public_xml(self.TEST_STRINGS_FILE, 'string', ResourceCategory.Value,
                                    self.TEST_DIMENS_MAP_START, self.TEST_PUBLIC_FILE)
        self.ota_util.update_ota_public_xml(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE,
                                            self.TEST_OTA_DIR, self.TEST_OTA_LAYOUT_DIR,
                                            self.TEST_OTA_DRAWABLE_DIR, self.TEST_OTA_COLOR_DIR)

        self._test_util_compare_public_xmls(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE)

        new_dimens_count = 1
        self._update_res_xml(self.TEST_DIMENS_FILE, 'dimen', new_dimens_count)
        new_ota_dimens_count = 1
        self._update_res_xml(self.TEST_OTA_DIMENS_FILE, 'dimen', new_ota_dimens_count)

        self.util.update_public_xml(self.TEST_DIMENS_FILE, 'dimen', ResourceCategory.Value,
                                    self.TEST_DIMENS_MAP_START, self.TEST_PUBLIC_FILE)
        self.ota_util.update_ota_public_xml(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE,
                                            self.TEST_OTA_DIR, self.TEST_OTA_LAYOUT_DIR,
                                            self.TEST_OTA_DRAWABLE_DIR, self.TEST_OTA_COLOR_DIR)

        self._test_util_compare_public_xmls(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE)

        self._write(self.TEST_LAYOUT_XML, self.TEST_LAYOUT_FILE)
        self._write(self.TEST_OTA_LAYOUT_XML, self.TEST_OTA_LAYOUT_FILE)

        self.util.update_public_xml(self.TEST_LAYOUT_DIR, 'layout', ResourceCategory.File,
                                    self.TEST_LAYOUT_MAP_START, self.TEST_PUBLIC_FILE)
        self.util.update_public_xml(self.TEST_LAYOUT_DIR, 'id', ResourceCategory.Id,
                                    self.TEST_ID_MAP_START, self.TEST_PUBLIC_FILE)

        self.ota_util.update_ota_public_xml(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE,
                                            self.TEST_OTA_DIR, self.TEST_OTA_LAYOUT_DIR,
                                            self.TEST_OTA_DRAWABLE_DIR, self.TEST_OTA_COLOR_DIR)

        self._test_util_compare_public_xmls(self.TEST_PUBLIC_FILE, self.TEST_OTA_PUBLIC_FILE)

        self._delete_all_test_files()


if __name__ == '__main__':
    unittest.main()
