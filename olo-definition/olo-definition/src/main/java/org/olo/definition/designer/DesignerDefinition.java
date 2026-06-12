package org.olo.definition.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Studio designer metadata on workflow artifacts ({@code designer} on workflows, agents, tools, hooks).
 */
@JsonDeserialize(builder = DesignerDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DesignerDefinition {

    private final String paletteGroup;
    private final List<String> searchKeywords;
    private final NodeSizeDefinition nodeSize;
    private final Boolean resizable;
    private final Boolean draggable;

    private DesignerDefinition(Builder builder) {
        this.paletteGroup = builder.paletteGroup;
        this.searchKeywords = builder.searchKeywords == null
                ? List.of()
                : List.copyOf(builder.searchKeywords);
        this.nodeSize = builder.nodeSize;
        this.resizable = builder.resizable;
        this.draggable = builder.draggable;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPaletteGroup() {
        return paletteGroup;
    }

    public List<String> getSearchKeywords() {
        return searchKeywords;
    }

    public NodeSizeDefinition getNodeSize() {
        return nodeSize;
    }

    public Boolean getResizable() {
        return resizable;
    }

    public Boolean getDraggable() {
        return draggable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DesignerDefinition that)) {
            return false;
        }
        return Objects.equals(paletteGroup, that.paletteGroup)
                && Objects.equals(searchKeywords, that.searchKeywords)
                && Objects.equals(nodeSize, that.nodeSize)
                && Objects.equals(resizable, that.resizable)
                && Objects.equals(draggable, that.draggable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paletteGroup, searchKeywords, nodeSize, resizable, draggable);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String paletteGroup;
        private List<String> searchKeywords;
        private NodeSizeDefinition nodeSize;
        private Boolean resizable;
        private Boolean draggable;

        public Builder paletteGroup(String paletteGroup) {
            this.paletteGroup = paletteGroup;
            return this;
        }

        public Builder searchKeywords(List<String> searchKeywords) {
            this.searchKeywords = searchKeywords;
            return this;
        }

        public Builder searchKeyword(String keyword) {
            if (keyword == null || keyword.isBlank()) {
                return this;
            }
            if (this.searchKeywords == null) {
                this.searchKeywords = new ArrayList<>();
            }
            this.searchKeywords.add(keyword);
            return this;
        }

        public Builder nodeSize(NodeSizeDefinition nodeSize) {
            this.nodeSize = nodeSize;
            return this;
        }

        public Builder nodeSize(int width, int height) {
            this.nodeSize = NodeSizeDefinition.builder().width(width).height(height).build();
            return this;
        }

        /** Legacy flat field — migrated to {@link #nodeSize}. */
        @JsonSetter("defaultWidth")
        public Builder legacyDefaultWidth(Integer width) {
            return nodeSizeWidth(width);
        }

        /** Legacy flat field — migrated to {@link #nodeSize}. */
        @JsonSetter("defaultHeight")
        public Builder legacyDefaultHeight(Integer height) {
            return nodeSizeHeight(height);
        }

        private Builder nodeSizeWidth(Integer width) {
            if (width == null) {
                return this;
            }
            NodeSizeDefinition.Builder size = nodeSize == null
                    ? NodeSizeDefinition.builder()
                    : NodeSizeDefinition.builder().width(nodeSize.getWidth()).height(nodeSize.getHeight());
            this.nodeSize = size.width(width).build();
            return this;
        }

        private Builder nodeSizeHeight(Integer height) {
            if (height == null) {
                return this;
            }
            NodeSizeDefinition.Builder size = nodeSize == null
                    ? NodeSizeDefinition.builder()
                    : NodeSizeDefinition.builder().width(nodeSize.getWidth()).height(nodeSize.getHeight());
            this.nodeSize = size.height(height).build();
            return this;
        }

        public Builder resizable(Boolean resizable) {
            this.resizable = resizable;
            return this;
        }

        public Builder draggable(Boolean draggable) {
            this.draggable = draggable;
            return this;
        }

        public DesignerDefinition build() {
            return new DesignerDefinition(this);
        }
    }
}
