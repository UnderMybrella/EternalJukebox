# Authentication
A few useful services are offered by the Eternal Jukebox through the use of logins.

Currently, the use case is limited simply to marking a song as "favourited", which allows a user to come back to that song.

# How it's done

Due to password security, I'm taking the liberty of not dealing with passwords *myself*, but instead handling sign ins through third party sites, such as Google, Spotify, and so forth.

When a user wishes to log in, they are presented with the list of sites. They then choose one to authenticate through.

If, however, the user has a valid cookie with a valid signed JWT token, they are instead presented with their profile page.

Logging in under a service first checks if there is a registered user in the database for that service for a unique identifier for that service. Normally, this will be a user ID or an email address.

If there is one, we return the snowflake ID for that user account (Generated with an epoch of 1489148833, 10/3/17 12:27:13 GMT). If there is an access token currently in use for the account, then we return that. If not, we generate one and return it.

If, however, we have no account for the identifier, we create a user account and associate the two accounts together. We then create an access token and return it.