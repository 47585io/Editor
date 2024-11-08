package com.parser.html.token;
import java.util.*;

public interface TokenStream
{
	public Token scanNextToken();

    public int getCurrentPosition();

    public void setCurrentPosition(int $pos);

    public int getEndOfFilePosition();

    public ArrayList<Token> getTokensArray();
}
