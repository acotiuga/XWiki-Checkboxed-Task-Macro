# XWiki-Checkboxed-Task-Macro
This [XWiki](https://github.com/xwiki/xwiki-platform) macro adds a **task with checkbox** macro and an event listener for ticking completed tasks off using the checkboxes.
![image](https://user-images.githubusercontent.com/8625511/211163903-8485e072-d8d3-4f5b-bb49-bef97553abad.png)

Additionally there is a a configurable **task report** macro that lists tasks from a given space:
![image](https://user-images.githubusercontent.com/8625511/211163840-a866e732-b274-4a72-9971-7e472ac22e44.png)


## Installation
Head over to [Releases](https://github.com/jmiba/XWiki-Checkboxed-Task-Macro/releases), download the .xar file, and import it in the [Wiki administration section](https://extensions.xwiki.org/xwiki/bin/view/Extension/Administration%20Application) under "Content" -> "Import"

## Configure CKEditor

In order to make best use of the macro, add the following CKeditor configuration to your editor through the [dedicated "WYSIWYG Editor" section](https://extensions.xwiki.org/xwiki/bin/view/Extension/CKEditor%20Integration/#HAdministrationSection) in the [Wiki administration](https://extensions.xwiki.org/xwiki/bin/view/Extension/Administration%20Application):


```
config['xwiki-macro'] = config['xwiki-macro'] || {};
config['xwiki-macro'].insertButtons = [
  {
    insertDirectly: false,
    macroCall: {
      name:'checktask',
      inline:'enforce',
    }
  }
];
```
This adds a clickable icon for quick insertion of a task:
![image](https://user-images.githubusercontent.com/8625511/211164104-d8602302-7c45-4229-bbab-bd2fb268b07c.png)

## Macros and components 

* **Checkboxed Task**: Contains the **checktask** macro that inserts checkboxed tasks in pages and a JavaScript listener that listens for checking/unchecking events
* **Task Listener**: Event listener that listens for creating and updating tasks on pages, adding XObjects to the respective pages
* **Task Updater**: Is called by the JavaScript listener to update the XObject of a task when checked or unchecked
* **Task Class**: XObject class that defines the properties of tasks to be saved 
* **Task Report Macro**: Contains the macro **reportchecktasks** that inserts a task report in pages using the default **live data** macro. Also contains another JavaScript listener listening for checking/unchecking events within the live data table.
* **Tasks JSON**: Defines the database query and formats the input for the live data macro in the task report
* **Translations**: Contains the localized labels of the task and task report macros
