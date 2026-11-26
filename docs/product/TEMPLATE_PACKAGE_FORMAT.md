# Template Package Format

Template Studio accepts either one ZIP file or a selection of individual files. Both upload paths create a validated staging package; upload does not publish or deploy a template.

## Required root files

Every package must contain these five files at the ZIP root:

- **index.html** — the template's page structure.
- **styles.css** — its visual design and responsive styling.
- **script.js** — its animation and interaction code.
- **template.xml** — the template identity, sections and field definitions.
- **preview.xml** — dummy values used by the admin preview.

The root files must use these exact names. Additional assets can be placed in subfolders, for example **assets/flowers.webp**, **assets/display.woff2**, **assets/intro.mp4**, **assets/animation.js** and **data/schedule.json**.

## XML definition

The root element is **template** and requires **id**, **name**, **version** and **schemaVersion**. The ID format is **TEMP** followed by seven digits.

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<template id="TEMP0000001" name="Floral Garden" version="1.0.0" schemaVersion="1">
  <sections>
    <section key="hero" label="Invitation">
      <field key="headline" label="Main heading" type="text" required="true"
             placeholder="The wedding of..." />
      <field key="intro" label="Invitation message" type="longtext" />
    </section>
    <section key="events" label="Functions" repeatable="true">
      <field key="eventName" label="Function name" type="text" required="true" />
      <field key="date" label="Date" type="date" required="true" />
      <field key="venue" label="Venue" type="text" />
    </section>
    <section key="family" label="Family details" optional="true">
      <field key="familyNames" label="Family names" type="longtext" />
    </section>
  </sections>
</template>
~~~

- A section is required by default. Set **optional="true"** when customers may hide it.
- A field is optional by default. Set **required="true"** when its value must be completed.
- **repeatable="true"** on a section allows the customer to add multiple entries, such as wedding functions.
- Supported field types include **text**, **longtext**, **date**, **time**, **email**, **phone**, **location**, **number** and **url**. **longtext**, **phone** and **location** map to the corresponding customer editor controls.
- Field keys and labels, section order, visual design and template code belong to the template. Customers provide values and may hide or restore only optional fields/sections.

## Preview data

**preview.xml** contains dummy data. Wrap sample fields in elements named after their section keys. For repeatable sections, use one or more **item** elements.

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<preview>
  <hero>
    <headline>A Beautiful Celebration</headline>
    <intro>Join us for a memorable day.</intro>
  </hero>
  <events>
    <item>
      <eventName>Mehndi</eventName>
      <date>2027-02-12</date>
      <venue>Garden Hall</venue>
    </item>
    <item>
      <eventName>Wedding Ceremony</eventName>
      <date>2027-02-14</date>
      <venue>Grand Estate</venue>
    </item>
  </events>
  <family>
    <familyNames>With love from both families</familyNames>
  </family>
</preview>
~~~

The preview script receives **window.digitalInvitationPreviewData**, containing the template title, the XML-derived sample content and the mapped template definition.

## Customer data binding

Use HTML data attributes to connect the XML field keys to customer-entered values. The renderer inserts values with DOM text APIs, not HTML parsing.

- Add `data-field="headline"` to the element that should display the XML field with key `headline`.
- If a field key is used in more than one section, add `data-section="hero"` to scope it to that section.
- Put an optional section wrapper on an element with `data-section-key="family"`; the wrapper is removed when the customer disables that optional section.
- For a repeatable section, add `data-repeat="events"` to its list container and place a direct-child `<template>` inside it. Use `data-field` on elements inside the template. The renderer clones the template once for each populated entry and hides the container when no entries have values.

Example:

~~~html
<section data-section-key="events">
  <div data-repeat="events">
    <template>
      <article>
        <h2 data-field="eventName"></h2>
        <p data-field="date"></p>
        <p data-field="venue"></p>
      </article>
    </template>
  </div>
</section>
~~~

The preview and published invitation use the same data-binding behavior. Include a reduced-motion alternative for animations and bundle all required assets locally.

## Assets and limits

Allowed assets include HTML, CSS, JavaScript, XML, SVG, PNG, JPEG, WebP, GIF, AVIF, WOFF2, TTF, OTF, MP4, WebM, MP3, OGG, JSON and TXT. Packages are limited to 150 files, 10 MB compressed and 25 MB expanded. Absolute paths, parent-directory traversal, duplicate paths and server-side executables are rejected.

## Preview isolation

The admin preview renders the uploaded **index.html**, CSS, JavaScript and supported assets inside an iframe with **sandbox="allow-scripts"** and a restrictive Content Security Policy. Network connections, external scripts, parent-frame access, nested frames, forms and plug-ins are blocked. Include required images, fonts, media and JavaScript dependencies in the package; do not rely on remote CDNs.

This preview is a review of the uploaded package, not a production publication. A validated package remains staged until the controlled classpath/Docker release pipeline is configured and an authorized administrator publishes it.
