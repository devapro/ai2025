# Troubleshooting Guide

## Common Issues and Solutions

### Login and Account Issues

#### Cannot Log In - "Invalid Credentials" Error

**Problem**: You're unable to log in and see an "Invalid credentials" error.

**Solutions**:
1. **Check your email address**: Ensure you're using the correct email (check for typos)
2. **Reset your password**: Click "Forgot Password?" on the login page
3. **Check for caps lock**: Password is case-sensitive
4. **Clear browser cache**: Old login data may be cached
5. **Try incognito/private mode**: This rules out browser extension conflicts

If none of these work, your account may be locked after multiple failed attempts. Contact support to unlock it.

#### Email Verification Link Not Working

**Problem**: The verification link in your email doesn't work or says "expired".

**Solutions**:
1. **Check link expiration**: Verification links expire after 24 hours
2. **Request a new link**: Log in and go to Settings > Account > Resend Verification
3. **Check for line breaks**: Some email clients break long URLs - copy the full link
4. **Try a different browser**: Some browsers block certain links

If you still can't verify, contact support with your registered email.

#### Two-Factor Authentication Code Not Working

**Problem**: Your 2FA code is rejected even though it looks correct.

**Solutions**:
1. **Check time sync**: Ensure your device clock is accurate (2FA codes are time-sensitive)
2. **Wait for new code**: Codes expire every 30 seconds - wait for a fresh one
3. **Use backup code**: If you saved backup codes during setup, use one of those
4. **Contact support**: If locked out, support can temporarily disable 2FA

To prevent this:
- Keep your device time set to automatic
- Save backup codes in a secure location during 2FA setup

### Document Upload Issues

#### Upload Fails or Gets Stuck

**Problem**: Document upload fails or appears stuck at a certain percentage.

**Solutions**:
1. **Check file size**: Maximum is 100 MB (Free/Basic) or 500 MB (Pro/Enterprise)
2. **Check internet connection**: Slow or unstable connection can cause failures
3. **Try a different browser**: Some browsers handle large uploads better
4. **Compress large files**: Use ZIP for multiple files or reduce PDF quality
5. **Check file format**: Ensure format is supported (PDF, DOCX, TXT, MD, XLSX, PPT)
6. **Disable VPN temporarily**: Some VPNs interfere with uploads

If uploads consistently fail, your firewall or network may be blocking our servers.

#### Document Not Appearing After Upload

**Problem**: Upload succeeds but document doesn't appear in your project.

**Solutions**:
1. **Refresh the page**: Sometimes display doesn't update automatically
2. **Wait a few minutes**: Large files take time to process
3. **Check the correct project**: Ensure you're viewing the right project/folder
4. **Check storage quota**: You may have exceeded your storage limit
5. **Check for error messages**: Look for notifications in the top-right corner

#### "Unsupported File Format" Error

**Problem**: You get an error about unsupported file format.

**Supported formats**:
- Documents: PDF, DOC, DOCX, TXT, MD, RTF
- Spreadsheets: XLS, XLSX, CSV
- Presentations: PPT, PPTX
- Images: JPG, PNG, GIF, SVG
- Archives: ZIP (automatically extracted)

**Solutions**:
1. Convert your file to a supported format
2. For uncommon formats, convert to PDF first
3. Contact support if you need a specific format supported

### Search Issues

#### Search Returns No Results

**Problem**: Search doesn't return any results even though you know the content exists.

**Solutions**:
1. **Wait for indexing**: New documents take 5-15 minutes to be indexed
2. **Check spelling**: Try alternative spellings or keywords
3. **Use fewer keywords**: Start broad, then narrow down
4. **Check filters**: Ensure you haven't applied filters that exclude results
5. **Check project/folder**: Ensure you're searching in the right location
6. **Try semantic search**: Ask a question instead of using keywords

#### Search Results Are Irrelevant

**Problem**: Search returns results but they're not what you're looking for.

**Solutions**:
1. **Use exact phrases**: Put phrases in quotes for exact matches: `"quarterly report"`
2. **Use search operators**: `title:keyword`, `type:pdf`, `date:2024`
3. **Filter by date**: Use the date filter to narrow results
4. **Exclude terms**: Use minus sign: `report -draft` (exclude drafts)

### Performance Issues

#### Page Loads Slowly

**Problem**: The application is slow or pages take long to load.

**Solutions**:
1. **Check internet speed**: Run a speed test at speedtest.net
2. **Clear browser cache**: Settings > Privacy > Clear Browsing Data
3. **Disable browser extensions**: Some extensions slow down web apps
4. **Close unused tabs**: Too many tabs can slow your browser
5. **Try a different browser**: Chrome usually performs best
6. **Check system resources**: Close other memory-intensive applications

#### Document Viewer is Laggy

**Problem**: Scrolling or editing documents is slow and laggy.

**Solutions**:
1. **File too large**: Break large documents into smaller files
2. **Too many images**: Compress or reduce image quality
3. **Complex formatting**: Simplify document formatting
4. **Use desktop app**: Desktop app is optimized for performance
5. **Upgrade browser**: Ensure you're using the latest browser version

### Collaboration Issues

#### Changes Not Syncing in Real-Time

**Problem**: You don't see collaborator's changes immediately.

**Solutions**:
1. **Refresh the page**: Force a manual sync
2. **Check internet**: Sync requires stable connection
3. **Check collaborator's connection**: They may be offline
4. **Wait 30 seconds**: Some changes batch for efficiency
5. **Check document permissions**: Ensure everyone has edit access

#### Cannot Share Document with Team Member

**Problem**: Unable to share document or team member doesn't receive invitation.

**Solutions**:
1. **Check email spelling**: Ensure email address is correct
2. **Check spam folder**: Invitations may be filtered as spam
3. **Check team limit**: Free plan has limits on team size
4. **Verify email domain**: Some corporate emails block automated emails
5. **Use share link instead**: Generate and send a share link manually

### Billing and Payment Issues

#### Payment Failed

**Problem**: Credit card payment fails during subscription or upgrade.

**Solutions**:
1. **Check card details**: Verify number, expiration, CVV
2. **Check bank balance**: Ensure sufficient funds
3. **Check international transactions**: Enable international payments if applicable
4. **Try a different card**: Some cards are rejected by our payment processor
5. **Use PayPal**: Alternative payment method
6. **Contact your bank**: They may have blocked the transaction

#### Charged Incorrectly

**Problem**: You were charged an unexpected amount.

**Solutions**:
1. **Check billing history**: Settings > Billing > History
2. **Understand billing cycle**: Charges occur on subscription renewal date
3. **Check for plan changes**: Upgrades are prorated and charged immediately
4. **Check team seats**: Additional team members may add to cost
5. **Contact support**: Provide transaction ID for investigation

We offer refunds within 30 days of initial subscription if there's an error.

### API and Integration Issues

#### API Returns 401 Unauthorized

**Problem**: API requests return 401 error.

**Solutions**:
1. **Check API key**: Ensure you're using a valid, active API key
2. **Check Authorization header**: Format: `Authorization: Bearer YOUR_API_KEY`
3. **Regenerate API key**: Settings > API > Regenerate Key
4. **Check key permissions**: Ensure key has required scope
5. **Check key expiration**: Some API keys expire after 90 days

#### API Rate Limit Exceeded

**Problem**: Getting 429 "Too Many Requests" errors.

**Rate limits by plan**:
- Free: 100 requests/hour
- Basic: 1,000 requests/hour
- Professional: 10,000 requests/hour
- Enterprise: Custom

**Solutions**:
1. **Implement exponential backoff**: Wait before retrying
2. **Cache responses**: Don't request the same data repeatedly
3. **Upgrade plan**: Get higher rate limits
4. **Optimize requests**: Use batch endpoints where available
5. **Monitor usage**: Track your API usage in Settings > API

#### Integration Not Working

**Problem**: Third-party integration (Slack, Google Drive, etc.) isn't functioning.

**Solutions**:
1. **Reconnect integration**: Settings > Integrations > Reconnect
2. **Check permissions**: Ensure you granted all required permissions
3. **Update integration**: Some integrations need periodic reauthorization
4. **Check service status**: Verify the third-party service is operational
5. **Review error logs**: Check integration logs for specific errors

### Mobile App Issues

#### App Crashes on Startup

**Problem**: Mobile app crashes immediately after opening.

**Solutions**:
1. **Update the app**: Check for updates in App Store/Google Play
2. **Restart your device**: Simple restart often fixes issues
3. **Clear app cache**: Settings > Apps > Platform Name > Clear Cache
4. **Reinstall app**: Uninstall and reinstall fresh
5. **Check OS version**: Requires iOS 14+ or Android 8+

#### Documents Not Syncing to Mobile

**Problem**: Changes on desktop don't appear on mobile or vice versa.

**Solutions**:
1. **Check internet connection**: Sync requires active connection
2. **Force sync**: Pull down to refresh on mobile
3. **Check storage**: Ensure device has sufficient storage
4. **Enable background sync**: Settings > Sync > Background Sync ON
5. **Log out and back in**: Refreshes sync connection

## Still Having Issues?

If you've tried the solutions above and still need help:

1. **Check Status Page**: Visit status.example.com for known outages
2. **Contact Support**:
   - Live Chat: Available 9 AM - 6 PM EST
   - Email: support@example.com
   - Phone: 1-800-SUPPORT (Enterprise)

When contacting support, provide:
- Description of the issue
- Steps to reproduce
- Browser/device information
- Screenshots if applicable
- Error messages (exact text)

We typically respond within 24 hours (faster for paid plans).
