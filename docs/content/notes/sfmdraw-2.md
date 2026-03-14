
# Terminology

Grab - to click and drag an element before releasing it.

Handle - a visual indication that part of an element can be grabbed.

# Introduction

let's do a big revamp on the systems.

We can make the ui customizable using the same tools.

right now the minimap and the toolbar are draggable.

they are currently draggable while working on the user layer, the only layer at this time

we will formally introduce layers to the sfm draw experience

the default layer is the user layer - where the user will create stuff

the second layer is the chrome layer - where the user can customize the user interface

layers can be switched to using alt+1 and alt+2 and etc

Layers will be used to quote the different parts of the application - the user layer for USING SFMDraw, the chrome layer for CUSTOMIZING SFMDraw

## The Layer Tool

the user will have a layer tool

The action of selecting the layer tool will make the layer window visible

## The Layer Window

the layer window purpose is to provide the user visual feedback about which layer they are on; the layer window layer list element corresponding to the active layer has a visually distinct appearance from the other layers list elements

the layer window has a title that says "Layers"

the layer window has a sunken area where the list of layers lives

each layer in the layer window layer list has a thumbnail and a name

each layer in the layer window layer list has a default colour 
each layer in the layer window layer list has a default cursor 
each layer in the layer window layer list has a cursor-hover colour
each layer in the layer window layer list has a cursor-hover cursor that indicates the element can be clicked

the layer window has a border

the layer window border colour is parameterized
the layer window border thickness is parameterized

the layer window exists as a rectangle element in the chrome layer canvas

only elements from the active layer are rendered
only elements from the active layer are 

the layer window chrome canvas rectangle is equivalent to a normal rectangle

the layer window can be repositioned by grabbing the top of the window
the layer window close button behaviour takes priority over the layer window click-drag repositioning behaviour
the layer window resize handle exists in the  can be resized by grabbing the bottom right corner
the window layer grab 

## Tools


Registries introduced to support SFM Draw MUST be implemented similarly to the registries for SFMTextEditors and SFMResourceTypes

The registry key resource location path for registries introduced to support SFM Draw are prefixed with "draw_"

Tools exist

there exists the registry of tools

The name for the registry of tools is draw_tools

Each tool has a name

Tool names support i18n

Tools have icons

There exists icon sets 

Icon sets should consist of visually cohesive image files

Icons should be visually recognizable when drawn to small areas

We should copy Icons from "C:\Users\TeamD\OneDrive\Documents\Backups\Old stuff of mine from NAS\Pictures\famfamfam_silk_icons_v013\icons\"

### Cursor tool

There exists the cursor tool

When the cursor tool is selected, clicking an element will modify the selected quality of the clicked element

### Elements are deep yo

element properties are implemented using a key-value dictionary

An element is constructed from a string identifier and an empty dictionary describing its properties

the properties dictionary for an element does not need to live inside the element object so long as the dictionary of properties for a given element id is easily obtained

there exists the registry of property definitions

the registry of property definitions has the id "draw_properties"

a property definition has an id

a property definition has a constraint

a property is defined as the entry in the dictionary of properties for a given element id where the key of the entry corresponds to the property definition id and the value of the property satisfies the property definition property constraint

a property definition id is defined as the id of that property definition in the property definition registry

there does not exist a registry of elements as elements are constructed and destroyed at runtime

there exists the x property

the x property is constrained to floating point numbers

the y property exists

the y property is constrained to floating point numbers

the id property exists

the id property of an element is defined as equalling to the id of the element

the id property is constrained to the constraints of element ids

Each element has a corresponding ElementId
the element id newtype wraps a String
the id of an element cannot exceed 256 bytes in length

an element can exist with no properties other than its id

The selection property exists

The selection property is constrained to Set<ElementId>

The ElementIdSet type exists satisfying Set<ElementId>

element ids must be unique
the id of an element should not change

The layer property exists
the layer property is constrained to utf-8 strings less than 256 bytes in length
If no element with the layer property of "default" exists,  an element with that property is created
If no element with the layer property of "chrome" exists, an element with that property is created
If no element with the layer property of "elements" exists, an element with that property is created

a selection is considered a rectangle if there are exactly four elements in the selection and there are exactly two unique x property values and exactly two unique y property values among the elements in the selection

for each element that exists with a layer property value where that value is not equal to "elements" there also should also exist an element with the layer property value of "elements" and 
for each element that exists without the layer property of that element having a value that corresponds to the layer id 

The right-most property takes priority


let us consider the details

a rectangle has a border colour, a background colour, a background pattern

there exists the z property

the z property is constrained to floating point numbers

the children property exists

the children property is constrained to ElementId(x) where x has the Selection property



Elements can be sorted by depth where each element is sorted by z default 0 where elements with Children(i, Selection(j)) have their z interpreted as relative biasing from the index of element i in the ordering such that siblings j are only above or below one another but are collectively above or below the siblings of their parent i

The name property exists
the name property is constrained to utf-8 strings less than 256 bytes in length

The text property exists
the text property is constrained to utf-8 strings less than 8192 bytes in length


now, we can construct a rectangle with text in it

x(e1,0). y(e1,0).
x(e2,0). y(e2,1).
x(e3,1). y(e3,0).
x(e4,1). y(e4,1).
selection(e5, {e1,e2,e3,e4}).
color(e6, red).
opacity(e6, 0.5).
rectangle(e6, e5). -- draw command
x(e7, 0.5). y(e7, 0.5).
centered(e7).
text(e7, "Ahoy, world!"). -- draw command
wind(eBorder, e5).
color(eBorder, red + darken).
stroke(eBorder, 3). -- draw command


how might we imagine two boxes connected by an arrow?
x(a1,0). y(a1,0).
x(a2,0). y(a2,1).
x(a3,1). y(a3,0).
x(a4,1). y(a4,1).

x(b1, x(a1,n); n+2). y(b1,0).
x(b2, x(a2,n); n+2). y(b2,1).
x(b3, x(a3,n); n+2). y(b3,0).
x(b4, x(a4,n); n+2). y(b4,1).

x(arrowTail, 1). y(arrowTail, 1).
x(arrowMid, 1.5). y(arrowMid, 1.5).
x(arrowHead, 2). y(arrowHead, 1).
line(arrow, [arrowTail, arrowMid, arrowHead]).
bezier(arrow).
stroke(arrow, 2). -- draw command
