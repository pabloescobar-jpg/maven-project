README - ClickCopyChat Plugin

====================================

This plugin makes all chat messages on a Paper server 'click-to-copy'.

It uses Paper's Adventure API to attach a ClickEvent.copyToClipboard to chat components.



File Structure:

-----------------

clickcopychat_extracted/
    plugin.yml
    META-INF/
        MANIFEST.MF
        maven/
            com.example/
                clickcopychat/
                    pom.xml
                    pom.properties
    com/
        example/
            clickcopychat/
                ClickCopyChat.class
                ClickCopyChat$1.class


Glossary / Explanations:

------------------------------------

plugin.yml :

    Defines the plugin metadata (name, version, main class, API version, etc).



META-INF/ :

    Contains Java package metadata used by Maven and the JAR manifest.

    - MANIFEST.MF : Standard JAR manifest, with plugin build info.

    - maven/... : Maven artifact info, including POM and properties.



com/example/clickcopychat/ :

    The compiled Java plugin classes.

    - ClickCopyChat.class : Main plugin class, extends JavaPlugin. Registers listeners.

    - ClickCopyChat$1.class : Inner class (probably the chat event listener) that modifies messages with click-to-copy behavior.
