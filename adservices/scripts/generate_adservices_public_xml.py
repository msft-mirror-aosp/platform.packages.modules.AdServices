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

import xml.etree.ElementTree as ET
import datetime
import os

class AdServicesUiUtil:

    PUBLIC_XML_DIR = '../apk/publicres/values/public.xml'
    STRINGS_XML_DIR = '../apk/res/values/strings.xml'
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
<!-- DO NOT MODIFY. This file is automatically generated by generate_adservices_public_xml.py -->\n'''

    def __init__(self):
        pass

    def _get_max_id(self, existing_mapping):
        return int(max(existing_mapping.values()), 0)

    def _get_existing_tree(self, dir):
        if not os.path.exists(dir):
            return None, {}
        else:
            root = ET.parse(dir).getroot()
            return root, {
                node.attrib['name']:node.attrib['id']
                for node in root
            }

    def _overwrite_public_xml(self, root, public_xml_dir):
        if os.path.exists(public_xml_dir):
            os.remove(public_xml_dir)

        ET.indent(root, space='    ')
        with open(public_xml_dir, "w+") as file:
            file.write(self.COPYRIGHT_TEXT)
            file.write(ET.tostring(root, encoding="unicode"))

    def update_public_xml(self, strings_xml_dir=STRINGS_XML_DIR, public_xml_dir=PUBLIC_XML_DIR):
        if not os.path.exists(strings_xml_dir):
            return

        new_strings= set(node.attrib['name'] for node in ET.parse(strings_xml_dir).getroot())
        root, mapping = self._get_existing_tree(public_xml_dir)

        added_strings = set(string for string in new_strings if string not in mapping)
        #TO-DO: add code to remove exsting elements when needed.
        deleted_strings = set(string for string in mapping if string not in new_strings)

        if not added_strings:
            return

        i_max = self._get_max_id(mapping)
        for string in added_strings:
            i_max += 1
            added_element = ET.SubElement(root, 'public')
            added_element.set('type', 'string')
            added_element.set('name', string)
            added_element.set('id', hex(i_max))

        self._overwrite_public_xml(root, public_xml_dir)

if __name__ == '__main__':
    util = AdServicesUiUtil()
    util.update_public_xml()
