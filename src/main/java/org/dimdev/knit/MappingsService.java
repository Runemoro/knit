package org.dimdev.knit;

import com.intellij.openapi.ui.Messages;
import cuchaz.enigma.mapping.Mappings;
import cuchaz.enigma.mapping.MappingsEnigmaReader;
import cuchaz.enigma.mapping.MappingsEnigmaWriter;
import cuchaz.enigma.throwables.MappingParseException;

import java.io.File;
import java.io.IOException;

public class MappingsService {
    private final MappingsEnigmaReader mappingsReader = new MappingsEnigmaReader();
    private final MappingsEnigmaWriter mappingsWriter = new MappingsEnigmaWriter();
    private File mappingDirectory = null;
    private Mappings mappings = null;

    public Mappings getMappings() {
        return mappings;
    }

    public void loadMappings(File mappingDirectory) {
        this.mappingDirectory = mappingDirectory;
        mappings = new Mappings();

        try {
            mappingsReader.readDirectory(mappings, mappingDirectory);
        } catch (MappingParseException e) {
            Messages.showErrorDialog("Exception occured while parsing mappings: " + e, "Mapping Parse Error");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveMappings() {
        if (mappings == null) {
            return;
        }

        try {
            mappingsWriter.writeAsDirectory(mappingDirectory, mappings);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void clearMappings() {
        mappingDirectory = null;
        mappings = null;
    }

    public boolean hasMappings() {
        return mappings != null;
    }
}
