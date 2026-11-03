import zipfile
import xml.etree.ElementTree as ET

NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'

def extract():
    z = zipfile.ZipFile('docs/Ma_tran_truy_vet_va_test_case.xlsx')
    
    # 1. Shared strings
    strings = []
    if 'xl/sharedStrings.xml' in z.namelist():
        tree = ET.fromstring(z.read('xl/sharedStrings.xml'))
        for si in tree.findall(f'{NS}si'):
            text = ''.join(si.itertext())
            strings.append(text)
            
    # 2. Sheet names
    sheets = []
    if 'xl/workbook.xml' in z.namelist():
        wb_tree = ET.fromstring(z.read('xl/workbook.xml'))
        for sheet in wb_tree.findall(f'.//{NS}sheet'):
            sheets.append((sheet.attrib.get('name'), sheet.attrib.get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id')))

    with open('docs/test_cases_extracted.txt', 'w', encoding='utf-8') as out:
        out.write(f"Sheets: {[s[0] for s in sheets]}\n\n")
        out.write("=== RELEVANT STRINGS (AT / UT / ET / Catalog / Category / Product / Cart) ===\n")
        for s in strings:
            if any(k in s for k in ['AT-', 'UT-', 'ET-', 'Category', 'Product', 'Catalog', 'cart', 'Cart', 'POST', 'DELETE', 'GET']):
                out.write(s + '\n')

    print("Extraction completed into docs/test_cases_extracted.txt")

if __name__ == '__main__':
    extract()
