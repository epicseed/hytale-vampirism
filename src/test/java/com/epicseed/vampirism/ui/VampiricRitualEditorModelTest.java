package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplateRegistry;

class VampiricRitualEditorModelTest {

    @Test
    void exportsEditedPointAndTemplateJson() {
        VampiricRitualEditorModel model = VampiricRitualEditorModel.fromRegistry(new VampiricRitualTemplateRegistry());

        model.cyclePoint(1);
        model.cycleSymbol(1);
        model.nudgePoint('x', 0.02d);
        model.nudgeStep('z', -0.02d);
        model.exportSelectedPoint();

        String pointExport = model.exportText();
        assertTrue(pointExport.contains("\"id\": \"north_east\""));
        assertTrue(pointExport.contains("\"symbolId\": \"blood_spiral\""));
        assertTrue(pointExport.contains("\"offsetX\": 2.87"));

        model.exportSelectedTemplate();
        String templateExport = model.exportText();
        assertTrue(templateExport.contains("\"ritualId\": \"awakening\""));
        assertTrue(templateExport.contains("\"points\": ["));
        assertTrue(templateExport.contains("\"symbolId\": \"blood_spiral\""));
    }
}
