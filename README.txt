Projet ChatOS de Benjamin Jedrocha et Florian Durand

src/ : contient les fichiers sources du projet.
module/ : contient le module java.
test/ : contient les fichiers de test.
doc/ : contient la javadoc, le RFC, le manuel et le rapport.
jar/ : contient les deux jars (client et serveur) avec deux dossiers permettant de tester les requêtes HTTP.
build.xml : fichier de build ant pour build les deux jars ainsi que la javadoc.

Pour démarrer le serveur :
1. Ouvrir un terminal se déplacer dans le dossier "jar/".
2. Entrer par exemple "java -jar ServeurChatOS.jar 7777" cela va créer un serveur sur le port 7777.

Pour démarrer un client : 
1. Ouvrir un terminal se déplacer dans le dossier "jar/".
2. Entrer par exemple "java -jar ClientChatOS.jar TxtFolder Bob localhost 7777" cela va créer un client qui va 
chercher les ressources dans le dossier "TxtFolder/", avec le login Bob connecté au server créé précédemment sur le port 7777.