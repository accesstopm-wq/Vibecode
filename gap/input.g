SetUserPreference("UseColorPrompt", false);;
SetUserPreference("UseColorsInTerminal", false);;
ColorPrompt(false);;

C2 := CyclicGroup(2);;
S3 := SymmetricGroup(3);;
W := WreathProduct(C2, S3);;
S6 := SymmetricGroup(6);;

emb := IsomorphicSubgroups(S6, W)[1];;
H := Image(emb);;

Print(Elements(H), "\n");
