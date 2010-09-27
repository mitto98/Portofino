/*
 * Copyright (C) 2005-2010 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * Unless you have purchased a commercial license agreement from ManyDesigns srl,
 * the following license terms apply:
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * There are special exceptions to the terms and conditions of the GPL
 * as it is applied to this software. View the full text of the
 * exception in file OPEN-SOURCE-LICENSE.txt in the directory of this
 * software distribution.
 *
 * This program is distributed WITHOUT ANY WARRANTY; and without the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see http://www.gnu.org/licenses/gpl.txt
 * or write to:
 * Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307  USA
 *
 */

package com.manydesigns.elements.fields;

import com.manydesigns.elements.AbstractElementsTest;
import com.manydesigns.elements.annotations.Required;
import com.manydesigns.elements.forms.FieldSet;
import com.manydesigns.elements.forms.Form;
import com.manydesigns.elements.forms.FormBuilder;

import java.util.Map;

/*
* @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
* @author Angelo Lupo          - angelo.lupo@manydesigns.com
* @author Giampiero Granatella - giampiero.granatella@manydesigns.com
*/
public class SelectFieldTest2 extends AbstractElementsTest {
    public static final String copyright =
            "Copyright (c) 2005-2010, ManyDesigns srl";

    Integer[][] valuesArray = {
            {1, 1},
            {1, 2},
            {1, 3},
            {2, 4}
    };

    String[][] labelsArray = {
            {"paperino", "qui"},
            {"paperino", "quo"},
            {"paperino", "qua"},
            {"cip", "ciop"}
    };

    DefaultOptionProvider optionProvider;

    Form form;
    SelectField selectField1;
    SelectField selectField2;

    public void setUp() throws Exception {
        super.setUp();

        optionProvider = DefaultOptionProvider.create(
                2, valuesArray, labelsArray);

        form = new FormBuilder(Bean.class)
                .configOptionProvider(optionProvider, "p1", "p2")
                .build();
        FieldSet fieldSet = form.get(0);
        selectField1 = (SelectField) fieldSet.get(0);
        selectField2 = (SelectField) fieldSet.get(1);
    }

    public void testSelectField1() {
        assertEquals(optionProvider, selectField1.getOptionProvider());
        assertEquals(0, selectField1.getOptionProviderIndex());

        assertEquals(optionProvider, selectField2.getOptionProvider());
        assertEquals(1, selectField2.getOptionProviderIndex());
    }

    public void testSelectField2() {
        Bean bean = new Bean(null, null);
        form.readFromObject(bean);
        assertFalse(form.validate());

        assertNull(optionProvider.getValue(0));
        assertNull(optionProvider.getValue(1));
    }

    public void testSelectField3() {
        Bean bean = new Bean(2, null);
        form.readFromObject(bean);
        assertFalse(form.validate());

        assertNotNull(optionProvider.getValue(0));
        assertEquals(2, optionProvider.getValue(0));
        assertNull(optionProvider.getValue(1));
    }


    public void testSelectField4() {
        Bean bean = new Bean(null, 2);
        form.readFromObject(bean);
        assertFalse(form.validate());

        assertNull(optionProvider.getValue(0));
        assertNull(optionProvider.getValue(1));

        checkOptions1();
    }

    private void checkOptions1() {
        Map<Object,String> options0 = optionProvider.getOptions(0);
        assertNotNull(options0);
        assertEquals(2, options0.size());
        assertEquals("paperino", options0.get(1));
        assertEquals("cip", options0.get(2));

        Map<Object,String> options1 = optionProvider.getOptions(1);
        assertNotNull(options1);
        assertEquals(0, options1.size());
    }

    public void testSelectField5() {
        Bean bean = new Bean(3, 1);
        form.readFromObject(bean);
        assertFalse(form.validate());

        assertNull(optionProvider.getValue(0));
        assertNull(optionProvider.getValue(1));

        checkOptions1();
    }

    public void testSelectField6() {
        Bean bean = new Bean(1, 4);
        form.readFromObject(bean);
        assertFalse(form.validate());

        assertEquals(1, optionProvider.getValue(0));
        assertEquals(null, optionProvider.getValue(1));

        checkOptions2();
    }

    private void checkOptions2() {
        Map<Object,String> options0 = optionProvider.getOptions(0);
        assertNotNull(options0);
        assertEquals(2, options0.size());
        assertEquals("paperino", options0.get(1));
        assertEquals("cip", options0.get(2));

        Map<Object,String> options1 = optionProvider.getOptions(1);
        assertNotNull(options1);
        assertEquals(3, options1.size());
        assertEquals("qui", options1.get(1));
        assertEquals("quo", options1.get(2));
        assertEquals("qua", options1.get(3));
    }

    public void testSelectField7() {
        Bean bean = new Bean(1, 3);
        form.readFromObject(bean);
        assertTrue(form.validate());

        assertEquals(1, optionProvider.getValue(0));
        assertEquals(3, optionProvider.getValue(1));

        checkOptions2();
    }

    public static class Bean {
        @Required
        public Integer p1;

        @Required
        public Integer p2;

        public Bean(Integer p1, Integer p2) {
            this.p1 = p1;
            this.p2 = p2;
        }
    }
}
