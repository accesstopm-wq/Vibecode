SetUserPreference("UseColorPrompt", false);;
SetUserPreference("UseColorsInTerminal", false);;
ColorPrompt(false);;

C2 := CyclicGroup(2);;
S3 := SymmetricGroup(3);;
W := WreathProduct(C2, S3);;
S6 := SymmetricGroup(6);;

a := (1,2);;
b := (3,4);;
c := (5,6);;
x := (1,3)(2,4);;
y := (3,5)(4,6);;
H := Group(a,b,c,x,y);;

Print(Elements(H), "\n");
