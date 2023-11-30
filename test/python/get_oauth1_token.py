#!python3

"""
Example to illustrate the process of getting an Oauth1 access token from
Autodesk servers.
"""

# Autodesk Oauth1 documentation available here:
# https://wiki.autodesk.com/pages/viewpage.action?pageId=42437358

import requests
import subprocess   # nosec
import urllib
from builtins import input
from requests_oauthlib import OAuth1

oauth1_consumer_key = '{{OAuth1Key}}'
oauth1_consumer_secret = '{{OAuth1Secret}}'

def run():
    unauthorised_request_token = get_unauthorised_request_token(oauth1_consumer_key,
                                                                oauth1_consumer_secret)
    authorise_request_token(unauthorised_request_token['oauth_token'])
    access_token = get_access_token(oauth1_consumer_key,
                                    oauth1_consumer_secret,
                                    unauthorised_request_token['oauth_token'],
                                    unauthorised_request_token['oauth_token_secret'])
    for k in access_token:
        print("%s: %s" % (k, access_token[k]))

def get_unauthorised_request_token(consumer_key, consumer_secret):
    url = 'https://accounts-dev.autodesk.com/OAuth/RequestToken'
    auth = OAuth1(client_key=consumer_key,
                  client_secret=consumer_secret)
    resp = requests.post(url, auth=auth)
    return dict([[urllib.parse.unquote(y) for y in x.split('=')] for x in resp.text.split('&')])

def authorise_request_token(oauth_token):
    url = 'https://accounts-dev.autodesk.com/OAuth/Authorize?oauth_token=' + urllib.parse.quote(oauth_token)
    subprocess.call('start ' + url, shell=True)
    input("Please log in and then press enter to continue..")

def get_access_token(oauth_consumer_key,
                     oauth_consumer_secret,
                     oauth_request_token,
                     oauth_request_token_secret):
    url = 'https://accounts-dev.autodesk.com/OAuth/AccessToken'
    auth = OAuth1(oauth_consumer_key,
                     oauth_consumer_secret,
                     oauth_request_token,
                     oauth_request_token_secret)
    resp = requests.post(url,
                         auth = auth)
    return dict([[urllib.parse.unquote(y) for y in x.split('=')] for x in resp.text.split('&')])


if __name__=='__main__':
    run()

