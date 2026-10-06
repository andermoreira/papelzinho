"""Remove conversation content while preserving observed preview selectors."""
import sys
import xml.etree.ElementTree as ET

allowed = {'view_once_toggle', 'send_media_btn', 'media_container', 'caption_input'}
for path in sys.argv[1:]:
    tree = ET.parse(path)
    for node in tree.iter('node'):
        resource = node.get('resource-id', '').split('/')[-1]
        if resource not in allowed:
            for key in ('text', 'content-desc', 'hint', 'state-description'):
                if node.get(key):
                    node.set(key, '[redacted]')
        elif resource == 'caption_input':
            node.set('text', '')
    tree.write(path, encoding='utf-8', xml_declaration=True)
