Secure Cloud Grep
=================

Summary:

A system in Java to store, search and retrieve files securely from the cloud. It indexes the files on the client before storage for efficient search and retrieval.

Details:

The client indexes local files, encrypt and upload them to the server in an anonymized manner.

The user can search for a keyword (similar to grep) on the client which uses the index to determine the file to download. The client downloads it, decrypts it and stores it back on the machine with the correct file name from the index.
